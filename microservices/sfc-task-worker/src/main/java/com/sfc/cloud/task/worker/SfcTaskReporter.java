package com.sfc.cloud.task.worker;

import com.sfc.cloud.task.worker.protocol.TaskAbandonParam;
import com.sfc.cloud.task.worker.protocol.TaskFinishReportParam;
import com.sfc.cloud.task.worker.protocol.TaskLogReportParam;
import com.sfc.cloud.task.worker.protocol.TaskProgressReportParam;
import com.sfc.cloud.task.worker.protocol.TaskReportStartParam;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务执行上报器：负责进度节流上报、日志批量上报与结束/放弃上报。
 *
 * <p>进度与日志由 {@link SfcTaskWorkerRuntime} 以约 1 秒的固定周期调用 {@link #tick()} 驱动：
 * 每次 tick 对全部运行中任务刷新一次进度（速度由 SDK 根据相邻两次采样的差值计算）并批量上报
 * 已缓冲的日志行；任务结束时会冲刷残余日志，随结束上报一并提交。</p>
 *
 * <p>每个任务的「上报 / 结束 / 放弃」通过执行上下文对象锁串行化，避免结束上报后仍有进度或日志
 * 上报到达 core 而被判为过期数据。</p>
 */
@Slf4j
class SfcTaskReporter {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 单次日志上报的最大行数
     */
    private static final int LOG_BATCH_SIZE = 500;

    /**
     * 单次 tick 内最多上报的日志批次数（避免日志产出速度超过 HTTP 上报速度时长时间占用线程）
     */
    private static final int MAX_LOG_BATCHES_PER_TICK = 10;

    /**
     * 结束上报时携带的尾部日志最大行数
     */
    private static final int LOG_TAIL_MAX_LINES = 5000;

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
            return client.reportStart(new TaskReportStartParam(execution.getTaskId(), identity.getWorkerId()));
        } catch (Throwable e) {
            log.warn("{}任务[{}]开始上报失败（任务仍会继续执行）: {}", LOG_PREFIX, execution.getTaskId(), describe(e));
            return true;
        }
    }

    /**
     * 上报任务结束（完成/失败/取消），并携带尚未上报的尾部日志。
     *
     * <p>若任务已在本实例被标记结束（例如停机时已上报放弃），本方法直接返回。</p>
     *
     * @param execution 执行上下文
     * @param status    结束状态：2=FINISH，3=FAILED，4=CANCEL
     * @param message   结束说明
     */
    void reportFinish(SfcTaskExecution execution, int status, String message) {
        synchronized (execution.getReportLock()) {
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
                boolean accepted = client.reportFinish(new TaskFinishReportParam(execution.getTaskId(),
                        identity.getWorkerId(), status, message, tailLogLines));
                if (!accepted) {
                    log.warn("{}任务[{}]结束上报未被接受（任务可能已被回收或取消）", LOG_PREFIX, execution.getTaskId());
                }
            } catch (Throwable e) {
                log.warn("{}任务[{}]结束上报失败: {}", LOG_PREFIX, execution.getTaskId(), describe(e));
            }
        }
    }

    /**
     * 上报放弃任务执行权（任务会被 core 重置回等待中）。
     *
     * <p>若任务已在本实例被标记结束，本方法直接返回。目前仅在 Worker 优雅停机时调用，
     * 放弃原因固定为「执行者停机」。</p>
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
                boolean accepted = client.reportAbandon(new TaskAbandonParam(execution.getTaskId(),
                        identity.getWorkerId(), "执行者停机，放弃任务执行权"));
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
            SfcTaskProgress progress = execution.getProgress();
            long loaded = progress.getLoaded();
            long total = progress.getTotal();
            ProgressReportState state = progressStates.computeIfAbsent(execution.getTaskId(),
                    ignored -> new ProgressReportState(loaded, now));
            long elapsedMillis = Math.max(0L, now - state.lastReportTime);
            long speed = elapsedMillis > 0L
                    ? Math.max(0L, (loaded - state.lastLoaded) * 1000L / elapsedMillis)
                    : 0L;
            state.lastLoaded = loaded;
            state.lastReportTime = now;
            client.reportProgress(new TaskProgressReportParam(execution.getTaskId(),
                    identity.getWorkerId(), loaded, total, speed, now));
        }
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
}
