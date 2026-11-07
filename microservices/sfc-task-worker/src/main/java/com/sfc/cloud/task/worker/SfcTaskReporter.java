package com.sfc.cloud.task.worker;

import com.sfc.cloud.task.worker.protocol.TaskAbandonParam;
import com.sfc.cloud.task.worker.protocol.TaskFinishReportParam;
import com.sfc.cloud.task.worker.protocol.TaskLogReportParam;
import com.sfc.cloud.task.worker.protocol.TaskProgressReportParam;
import com.sfc.cloud.task.worker.protocol.TaskReportStartParam;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 任务执行上报器：负责进度节流上报、日志批量上报与结束/放弃上报。
 *
 * <p>同一任务的上报、结束与放弃流程通过执行上下文串行化，结束上报后不会再有进度或日志上报。</p>
 *
 * <p>进度上报优先使用执行上下文设置的完整进度快照提供者（内嵌执行者使用），从中读取
 * {@code loaded}/{@code total} 并把其余字段作为扩展信息上报；未设置提供者时使用进度对象。</p>
 */
@Slf4j
class SfcTaskReporter {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 进度快照中的已完成量键名
     */
    private static final String KEY_LOADED = "loaded";

    /**
     * 进度快照中的总量键名
     */
    private static final String KEY_TOTAL = "total";

    /**
     * 进度快照中的速度键名（速度由 SDK 计算，不作为扩展信息上报）
     */
    private static final String KEY_SPEED = "speed";

    /**
     * 进度快照中的最后更新时间键名（由 SDK 填充，不作为扩展信息上报）
     */
    private static final String KEY_LAST_UPDATE_TIME = "lastUpdateTime";

    /**
     * 单次日志上报的最大行数
     */
    private static final int LOG_BATCH_SIZE = 500;

    /**
     * 单次 tick 内最多上报的日志批次数
     */
    private static final int MAX_LOG_BATCHES_PER_TICK = 10;

    /**
     * 结束上报时携带的尾部日志最大行数
     */
    private static final int LOG_TAIL_MAX_LINES = 5000;

    /**
     * 关键上报（开始/结束/放弃）的最大尝试次数
     */
    private static final int REPORT_MAX_ATTEMPTS = 3;

    /**
     * 关键上报重试的基础退避时间（毫秒），第 n 次重试等待 n 倍
     */
    private static final long REPORT_RETRY_BACKOFF_MILLIS = 500L;

    /**
     * 运行中任务注册表
     */
    private final SfcTaskWorkerRegistry registry;

    /**
     * 内部协议客户端
     */
    private final SfcTaskWorkerClient client;

    /**
     * 当前实例身份信息
     */
    private final SfcTaskWorkerIdentity identity;

    /**
     * 每个任务的进度上报状态（上次采样值），用于计算速度
     */
    private final Map<Long, ProgressReportState> progressStates = new ConcurrentHashMap<>();

    /**
     * 构造上报器（由自动装配创建）。
     *
     * @param registry 运行中任务注册表
     * @param client   内部协议客户端
     * @param identity 当前实例身份信息
     */
    SfcTaskReporter(SfcTaskWorkerRegistry registry, SfcTaskWorkerClient client, SfcTaskWorkerIdentity identity) {
        this.registry = registry;
        this.client = client;
        this.identity = identity;
    }

    /**
     * 执行一次周期上报：刷新全部运行中任务的进度并批量上报日志。
     */
    void tick() {
        Set<Long> activeTaskIds = new HashSet<>();
        for (SfcTaskExecution execution : registry.getRunningExecutions()) {
            activeTaskIds.add(execution.getTaskId());
            try {
                reportExecution(execution);
            } catch (Throwable e) {
                log.warn("{}任务[{}]进度/日志上报失败: {}", LOG_PREFIX, execution.getTaskId(), describe(e));
            }
        }
        progressStates.keySet().retainAll(activeTaskIds);
    }

    /**
     * 上报任务开始执行。
     *
     * @param execution 执行上下文
     * @return core 是否接受；网络异常时返回 true（任务继续执行，后续上报会在网络恢复后继续）
     */
    boolean reportStart(SfcTaskExecution execution) {
        try {
            return withRetry("开始", execution.getTaskId(),
                    () -> client.reportStart(new TaskReportStartParam(execution.getTaskId(), identity.getWorkerId())));
        } catch (Throwable e) {
            log.warn("{}任务[{}]开始上报失败（任务仍会继续执行）: {}", LOG_PREFIX, execution.getTaskId(), describe(e));
            return true;
        }
    }

    /**
     * 上报任务结束（完成/失败/取消），并携带尚未上报的尾部日志；任务已标记结束则直接返回。
     *
     * @param execution 执行上下文
     * @param status    结束状态：2=FINISH，3=FAILED，4=CANCEL
     * @param message   结束说明
     */
    void reportFinish(SfcTaskExecution execution, int status, String message) {
        synchronized (execution.getReportLock()) {
            if (execution.isFinished()) {
                return;
            }
            // 补发最终进度快照：周期上报约 1 秒一次，若不补发，完成态进度可能比实际少 1~2 步
            reportFinalProgress(execution);
            if (!execution.markFinished()) {
                return;
            }
            progressStates.remove(execution.getTaskId());
            execution.flushPendingLogLine();
            List<String> tailLogLines = new ArrayList<>();
            while (tailLogLines.size() < LOG_TAIL_MAX_LINES) {
                List<String> batch = execution.drainLogLines(LOG_BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                tailLogLines.addAll(batch);
            }
            try {
                boolean accepted = withRetry("结束", execution.getTaskId(),
                        () -> client.reportFinish(new TaskFinishReportParam(execution.getTaskId(),
                                identity.getWorkerId(), status, message, tailLogLines)));
                if (!accepted) {
                    log.warn("{}任务[{}]结束上报未被接受（任务可能已被回收或取消）", LOG_PREFIX, execution.getTaskId());
                }
            } catch (Throwable e) {
                log.warn("{}任务[{}]结束上报失败: {}", LOG_PREFIX, execution.getTaskId(), describe(e));
            }
        }
    }

    /**
     * 补发一次最终进度快照（结束上报前调用）；失败只记录 debug 日志。
     *
     * @param execution 执行上下文
     */
    private void reportFinalProgress(SfcTaskExecution execution) {
        try {
            ProgressSnapshot snapshot = resolveProgress(execution);
            long now = System.currentTimeMillis();
            ProgressReportState state = progressStates.remove(execution.getTaskId());
            long lastLoaded = state == null ? 0L : state.lastLoaded;
            long elapsedMillis = state == null ? 0L : Math.max(0L, now - state.lastReportTime);
            long speed = elapsedMillis > 0L
                    ? Math.max(0L, (snapshot.loaded() - lastLoaded) * 1000L / elapsedMillis)
                    : 0L;
            client.reportProgress(new TaskProgressReportParam(execution.getTaskId(), identity.getWorkerId(),
                    snapshot.loaded(), snapshot.total(), speed, now, snapshot.extensions()));
        } catch (Throwable e) {
            log.debug("{}任务[{}]最终进度补发失败: {}", LOG_PREFIX, execution.getTaskId(), describe(e));
        }
    }

    /**
     * 上报放弃任务执行权（任务会被 core 重置回等待中）；任务已标记结束则直接返回。
     *
     * @param execution 执行上下文
     */
    void reportAbandon(SfcTaskExecution execution) {
        synchronized (execution.getReportLock()) {
            if (!execution.markFinished()) {
                return;
            }
            progressStates.remove(execution.getTaskId());
            try {
                boolean accepted = withRetry("放弃", execution.getTaskId(),
                        () -> client.reportAbandon(new TaskAbandonParam(execution.getTaskId(),
                                identity.getWorkerId(), "执行者停机，放弃任务执行权")));
                if (!accepted) {
                    log.warn("{}任务[{}]放弃上报未被接受（任务可能已被取消或达到最大重试次数）",
                            LOG_PREFIX, execution.getTaskId());
                }
            } catch (Throwable e) {
                log.warn("{}任务[{}]放弃上报失败: {}", LOG_PREFIX, execution.getTaskId(), describe(e));
            }
        }
    }

    /**
     * 对关键上报执行有限重试。
     *
     * @param action  动作名称（用于日志）
     * @param taskId  任务id
     * @param call    实际调用
     * @param <T>     返回值类型
     * @return 最后一次调用的返回值
     * @throws RuntimeException 全部尝试都失败时抛出最后一次的异常
     */
    private <T> T withRetry(String action, Long taskId, java.util.function.Supplier<T> call) {
        Throwable lastError = null;
        for (int attempt = 1; attempt <= REPORT_MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (Throwable e) {
                lastError = e;
                if (attempt < REPORT_MAX_ATTEMPTS) {
                    log.warn("{}任务[{}]{}上报失败（第 {}/{} 次），{}ms 后重试: {}", LOG_PREFIX, taskId, action,
                            attempt, REPORT_MAX_ATTEMPTS, REPORT_RETRY_BACKOFF_MILLIS * attempt, describe(e));
                    try {
                        Thread.sleep(REPORT_RETRY_BACKOFF_MILLIS * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("重试等待被中断", interrupted);
                    }
                }
            }
        }
        throw new IllegalStateException(action + "上报在 " + REPORT_MAX_ATTEMPTS + " 次尝试后仍然失败", lastError);
    }

    /**
     * 上报单个任务的进度与日志。
     *
     * @param execution 执行上下文
     */
    private void reportExecution(SfcTaskExecution execution) {
        synchronized (execution.getReportLock()) {
            if (execution.isFinished()) {
                return;
            }
            flushLogs(execution);
            long now = System.currentTimeMillis();
            ProgressSnapshot snapshot = resolveProgress(execution);
            long loaded = snapshot.loaded();
            long total = snapshot.total();
            ProgressReportState state = progressStates.computeIfAbsent(execution.getTaskId(),
                    ignored -> new ProgressReportState(loaded, now));
            long elapsedMillis = Math.max(0L, now - state.lastReportTime);
            long speed = elapsedMillis > 0L
                    ? Math.max(0L, (loaded - state.lastLoaded) * 1000L / elapsedMillis)
                    : 0L;
            state.lastLoaded = loaded;
            state.lastReportTime = now;
            client.reportProgress(new TaskProgressReportParam(execution.getTaskId(),
                    identity.getWorkerId(), loaded, total, speed, now, snapshot.extensions()));
        }
    }

    /**
     * 解析一次进度快照：执行上下文设置了进度快照提供者时以其为准，否则使用进度对象。
     *
     * <p>提供者返回 null 或读取失败时同样回退到进度对象，保证进度上报不中断。</p>
     *
     * @param execution 执行上下文
     * @return 进度快照（loaded / total / 扩展信息）
     */
    private ProgressSnapshot resolveProgress(SfcTaskExecution execution) {
        Supplier<Map<String, Object>> source = execution.getProgressSource();
        if (source != null) {
            try {
                Map<String, Object> snapshot = source.get();
                if (snapshot != null) {
                    long loaded = toLong(snapshot.get(KEY_LOADED), 0L);
                    long total = toLong(snapshot.get(KEY_TOTAL), -1L);
                    return new ProgressSnapshot(loaded, total, extractExtensions(snapshot));
                }
            } catch (Throwable e) {
                log.debug("{}任务[{}]读取进度快照失败，回退到进度对象: {}",
                        LOG_PREFIX, execution.getTaskId(), describe(e));
            }
        }
        SfcTaskProgress progress = execution.getProgress();
        return new ProgressSnapshot(progress.getLoaded(), progress.getTotal(), null);
    }

    /**
     * 从完整进度快照中提取扩展信息：去掉 SDK 已单独上报的进度字段。
     *
     * @param snapshot 完整进度快照
     * @return 扩展信息；没有扩展字段时返回 null
     */
    private static Map<String, Object> extractExtensions(Map<String, Object> snapshot) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            String key = entry.getKey();
            if (key == null || KEY_LOADED.equals(key) || KEY_TOTAL.equals(key)
                    || KEY_SPEED.equals(key) || KEY_LAST_UPDATE_TIME.equals(key)) {
                continue;
            }
            extensions.put(key, entry.getValue());
        }
        return extensions.isEmpty() ? null : extensions;
    }

    /**
     * 把快照片段解析为 long，无法识别时使用默认值。
     *
     * @param value        快照片段
     * @param defaultValue 默认值
     * @return 解析结果
     */
    private static long toLong(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof CharSequence text) {
            try {
                return Long.parseLong(text.toString().trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 批量上报已缓冲的日志行。
     *
     * @param execution 执行上下文
     */
    private void flushLogs(SfcTaskExecution execution) {
        for (int i = 0; i < MAX_LOG_BATCHES_PER_TICK; i++) {
            List<String> lines = execution.drainLogLines(LOG_BATCH_SIZE);
            if (lines.isEmpty()) {
                return;
            }
            client.reportLog(new TaskLogReportParam(execution.getTaskId(), identity.getWorkerId(), lines));
        }
    }

    /**
     * 异常简要描述：优先使用异常消息，消息为空时回退异常类型名。
     *
     * @param e 异常
     * @return 简要描述
     */
    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    /**
     * 单个任务的进度上报状态，用于计算每秒完成量。
     */
    private static final class ProgressReportState {

        /**
         * 上次采样时的已完成量
         */
        private long lastLoaded;

        /**
         * 上次采样时间（Unix 时间戳毫秒）
         */
        private long lastReportTime;

        /**
         * 构造进度上报状态。
         *
         * @param lastLoaded     初始已完成量
         * @param lastReportTime 初始采样时间
         */
        private ProgressReportState(long lastLoaded, long lastReportTime) {
            this.lastLoaded = lastLoaded;
            this.lastReportTime = lastReportTime;
        }
    }

    /**
     * 一次进度上报所需的快照数据。
     *
     * @param loaded     已完成量
     * @param total      总量，-1 表示未知
     * @param extensions 进度扩展信息，可能为 null
     */
    private record ProgressSnapshot(long loaded, long total, Map<String, Object> extensions) {
    }
}
