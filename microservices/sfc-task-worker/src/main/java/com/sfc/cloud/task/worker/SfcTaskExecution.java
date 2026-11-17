package com.sfc.cloud.task.worker;

import com.sfc.cloud.task.worker.protocol.TaskDispatchInfo;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 任务执行上下文。
 *
 * <p>一次任务执行对应一个实例（由 Worker SDK 创建，任务处理器只读取/使用，不要自行构造），
 * 向处理器暴露：</p>
 * <ul>
 *     <li>任务基本信息：{@link #getTaskId()}、{@link #getTaskName()}、{@link #getTaskType()}、
 *         {@link #getUid()}、{@link #getParams()}；</li>
 *     <li>日志输出流：{@link #getLogWriter()} 或 {@link #log(String)}，SDK 会缓冲并按批上报给 core；</li>
 *     <li>进度对象：{@link #getProgress()}（loaded / total）；</li>
 *     <li>完整进度快照：{@link #setProgressSource(Supplier)}，供内嵌执行者（如 core 本地任务）提供
 *         包含扩展字段的进度快照；</li>
 *     <li>中断协作：{@link #onInterrupt(Runnable)} 注册回调、{@link #isInterruptRequested()} 查询、
 *         {@link #checkInterrupted()} 在已中断时抛出 {@link SfcTaskInterruptedException}；</li>
 *     <li>检查点与挂起协作：{@link #getCheckpoint()} / {@link #saveCheckpoint(String)} 持久化任务现场，
 *         {@link #isDetachRequested()} / {@link #onDetach(Runnable)} 响应执行者停机挂起。</li>
 * </ul>
 *
 * <p>日志写入、中断回调与挂起回调注册均可在多线程场景下安全使用；日志行建议保持在单线程内顺序写入。</p>
 */
@Slf4j
public final class SfcTaskExecution {

    /**
     * 本地最多缓冲的日志行数，超出后按最旧优先丢弃。
     */
    private static final int MAX_BUFFERED_LOG_LINES = 5000;

    /**
     * 单次中断回调执行失败时写入的日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 任务id
     */
    @Getter
    private final Long taskId;

    /**
     * 任务名称
     */
    @Getter
    private final String taskName;

    /**
     * 任务类型
     */
    @Getter
    private final String taskType;

    /**
     * 任务创建者用户id
     */
    @Getter
    private final Long uid;

    /**
     * 任务参数（JSON 或纯文本，由任务处理器自行解析）
     */
    @Getter
    private final String params;

    /**
     * 执行任务的 worker 标识
     */
    @Getter
    private final String workerId;

    /**
     * 任务检查点：认领时由 core 下发，之后随 {@link #saveCheckpoint(String)} 更新。
     *
     * <p>首次返回认领时 core 下发的检查点，之后返回最近一次保存写入的值（即使该次同步上报最终失败，
     * 返回值仍是最新内容，便于任务实现对比与重试）；没有检查点时返回 null。</p>
     */
    @Getter
    private volatile String checkpoint;

    /**
     * 检查点保存器（由 Worker SDK 绑定，负责同步上报 core 与标记待重发）
     */
    private final CheckpointSaver checkpointSaver;

    /**
     * 待补发的检查点（同步上报失败时记录，由上报 tick 补发）
     */
    private final AtomicReference<String> pendingCheckpoint = new AtomicReference<>();

    /**
     * 挂起回调集合
     */
    private final List<Runnable> detachCallbacks = new CopyOnWriteArrayList<>();

    /**
     * 是否已收到挂起指令（执行者停机）
     */
    private final AtomicBoolean detachRequested = new AtomicBoolean(false);

    /**
     * 进度快照
     */
    @Getter
    private final SfcTaskProgress progress = new SfcTaskProgress();

    /**
     * 完整进度快照提供者；未设置时为 null（按 {@link #progress} 上报）
     * -- SETTER --
     *  设置完整进度快照提供者。
     *  <p>供内嵌执行者（如 sfc-core 本地任务）使用：进度快照以
     *  形式给出，
     *  其中键
     *  /
     *  为进度量，其余键（
     * 、
     *  除外）
     *  作为进度扩展信息一并上报；SDK 会在每个上报周期重新读取快照。</p>
     *  <p>微服务任务处理器无需使用本方法，直接通过
     *  更新进度即可。</p>
     *
     * @param progressSource 进度快照提供者，返回 null 时回退到 {@link #getProgress()} 的进度值；
     *                       传 null 表示取消提供者

     */
    @Setter
    private volatile Supplier<Map<String, Object>> progressSource;

    /**
     * 待上报的日志行缓冲
     */
    private final ConcurrentLinkedQueue<String> logLines = new ConcurrentLinkedQueue<>();

    /**
     * 因缓冲上限被丢弃的日志行数
     */
    private final AtomicInteger droppedLogLines = new AtomicInteger();

    /**
     * 中断回调集合
     */
    private final List<Runnable> interruptCallbacks = new CopyOnWriteArrayList<>();

    /**
     * 是否已收到中断指令
     */
    private final AtomicBoolean interruptRequested = new AtomicBoolean(false);

    /**
     * 是否已结束（结束上报或放弃上报后置位）
     */
    private final AtomicBoolean finished = new AtomicBoolean(false);

    /**
     * 上报与结束/放弃流程的互斥锁
     */
    private final Object reportLock = new Object();

    /**
     * 日志输出流
     * -- GETTER --
     *  获取日志输出流。
     *  <p>写入的内容会按行缓冲，由 SDK 定期（约 1 秒）批量上报给 core；任务结束时残余内容会随
     *  结束上报一并提交。
     *  会自动换行并触发刷新。</p>
     *
     * @return 日志输出流

     */
    @Getter
    private final PrintWriter logWriter;

    /**
     * 尚未遇到换行符的日志片段
     */
    private final StringBuilder pendingLine = new StringBuilder();

    /**
     * 构造执行上下文（仅由 Worker SDK 调用）。
     *
     * @param dispatchInfo    下发的任务信息
     * @param workerId        执行者标识
     * @param checkpointSaver 检查点保存器（由 Worker SDK 绑定，任务实现无需关心）
     */
    SfcTaskExecution(TaskDispatchInfo dispatchInfo, String workerId, CheckpointSaver checkpointSaver) {
        this.taskId = dispatchInfo.id();
        this.taskName = dispatchInfo.name();
        this.taskType = dispatchInfo.taskType();
        this.uid = dispatchInfo.uid();
        this.params = dispatchInfo.params();
        this.workerId = workerId;
        this.checkpoint = dispatchInfo.checkpoint();
        this.checkpointSaver = checkpointSaver;
        this.logWriter = new PrintWriter(new LineBufferingWriter(), true);
    }

    /**
     * 获取完整进度快照提供者（仅由 Worker SDK 的进度上报使用）。
     *
     * @return 进度快照提供者，未设置时返回 null
     */
    Supplier<Map<String, Object>> getProgressSource() {
        return progressSource;
    }

    /**
     * 保存任务检查点。
     *
     * <p>把任务自定义的现场快照同步上报到 core（带有限重试）；上报失败只记录 WARN 并把最新值记为
     * 待重发，由 SDK 的周期上报补发，<b>不会向任务实现抛出异常</b>。任务已结束时不保存。</p>
     *
     * @param checkpoint 检查点内容（JSON 文本，语义由任务实现自定义）；空白值视为不保存
     * @return 本次是否已成功同步到 core
     */
    public boolean saveCheckpoint(String checkpoint) {
        if (checkpoint == null || checkpoint.isBlank()) {
            return false;
        }
        if (isFinished() || checkpointSaver == null) {
            log.debug("{}任务[{}]已结束或未绑定检查点保存器，忽略检查点保存", LOG_PREFIX, taskId);
            return false;
        }
        this.checkpoint = checkpoint;
        try {
            boolean accepted = checkpointSaver.save(this, checkpoint);
            if (!accepted) {
                // 保存器可能只返回失败而不记录待重发，这里统一兜底记录，保证 tick 会补发
                pendingCheckpoint.set(checkpoint);
            }
            return accepted;
        } catch (Throwable e) {
            // 保存器按约定不抛异常，这里兜底保证任务实现不被打断
            log.warn("{}任务[{}]保存检查点异常: {}", LOG_PREFIX, taskId, describe(e));
            pendingCheckpoint.set(checkpoint);
            return false;
        }
    }

    /**
     * 输出一行运行日志（等价于向 {@link #getLogWriter()} 写入并换行）。
     *
     * @param line 日志内容，可包含换行符（会按行拆分）；为 null 时忽略
     */
    public void log(String line) {
        if (line == null) {
            return;
        }
        if (line.isEmpty()) {
            enqueueLine("");
            return;
        }
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '\n') {
                enqueueLine(stripCarriageReturn(line.substring(start, i)));
                start = i + 1;
            }
        }
        if (start < line.length()) {
            enqueueLine(stripCarriageReturn(line.substring(start)));
        }
    }

    /**
     * 注册中断回调。
     *
     * <p>回调应快速返回，耗时逻辑请异步处理；注册时任务已处于中断状态则立即执行一次。</p>
     *
     * @param callback 中断回调，为 null 时忽略
     */
    public void onInterrupt(Runnable callback) {
        if (callback == null) {
            return;
        }
        interruptCallbacks.add(callback);
        if (interruptRequested.get()) {
            runCallbackSafely(callback, "中断");
        }
    }

    /**
     * 判断是否已收到中断指令。
     *
     * @return 已收到中断指令返回 true
     */
    public boolean isInterruptRequested() {
        return interruptRequested.get();
    }

    /**
     * 检查任务是否已被中断，已中断时抛出中断异常。
     *
     * @throws SfcTaskInterruptedException 任务已被中断时抛出
     */
    public void checkInterrupted() {
        if (interruptRequested.get()) {
            throw new SfcTaskInterruptedException("任务[" + taskId + "]已被中断");
        }
    }

    /**
     * 判断是否已收到挂起指令（执行者停机）。
     *
     * <p>可挂起任务（{@link SfcTaskHandler#supportsDetach()} 返回 true）应轮询本方法检查是否需要
     * 保存检查点并退出本次执行。挂起与中断相互独立。</p>
     *
     * @return 已收到挂起指令返回 true
     */
    public boolean isDetachRequested() {
        return detachRequested.get();
    }

    /**
     * 注册挂起回调。
     *
     * <p>执行者停机时 SDK 会下发挂起指令并触发全部挂起回调，典型实现为「保存检查点 + 设置停止标志」；
     * 回调应快速返回，耗时逻辑请异步处理。注册时若挂起指令已到达则立即执行一次。挂起回调与中断回调相互独立。</p>
     *
     * @param callback 挂起回调，为 null 时忽略
     */
    public void onDetach(Runnable callback) {
        if (callback == null) {
            return;
        }
        detachCallbacks.add(callback);
        if (detachRequested.get()) {
            runCallbackSafely(callback, "挂起");
        }
    }

    /**
     * 标记任务已收到中断指令并触发全部中断回调（仅由 Worker SDK 调用）。
     *
     * <p>重复调用无副作用。</p>
     */
    void requestInterrupt() {
        if (!interruptRequested.compareAndSet(false, true)) {
            return;
        }
        log(LOG_PREFIX + "收到任务中断指令，正在通知执行逻辑尽快停止");
        for (Runnable callback : interruptCallbacks) {
            runCallbackSafely(callback, "中断");
        }
    }

    /**
     * 标记任务已收到挂起指令并触发全部挂起回调（仅由 Worker SDK 在停机流程中调用）。
     *
     * <p>重复调用无副作用；与 {@link #requestInterrupt()} 相互独立，不会触发中断回调。</p>
     */
    void requestDetach() {
        if (!detachRequested.compareAndSet(false, true)) {
            return;
        }
        log(LOG_PREFIX + "收到任务挂起指令，正在通知执行逻辑保存检查点并退出");
        for (Runnable callback : detachCallbacks) {
            runCallbackSafely(callback, "挂起");
        }
    }

    /**
     * 获取待补发的检查点（仅由 Worker SDK 的周期上报使用）。
     *
     * @return 待补发的检查点，没有时返回 null
     */
    String getPendingCheckpoint() {
        return pendingCheckpoint.get();
    }

    /**
     * 记录待补发的检查点（仅由 Worker SDK 在同步上报失败时调用）。
     *
     * @param checkpoint 待补发的检查点
     */
    void markPendingCheckpoint(String checkpoint) {
        pendingCheckpoint.set(checkpoint);
    }

    /**
     * 清除待补发的检查点（仅由 Worker SDK 在补发成功后调用）。
     *
     * @param checkpoint 已成功补发的检查点；只有与当前待补发值一致时才会清除，避免覆盖更新的内容
     */
    void clearPendingCheckpoint(String checkpoint) {
        pendingCheckpoint.compareAndSet(checkpoint, null);
    }

    /**
     * 获取上报互斥锁（仅由 Worker SDK 的进度/日志上报与结束上报使用）。
     *
     * @return 互斥锁对象
     */
    Object getReportLock() {
        return reportLock;
    }

    /**
     * 标记任务已结束（仅由 Worker SDK 调用）。
     *
     * @return 本次调用是否成功置位（已结束过则返回 false）
     */
    boolean markFinished() {
        return finished.compareAndSet(false, true);
    }

    /**
     * 判断任务是否已结束（结束上报或放弃上报）。
     *
     * @return 已结束返回 true
     */
    boolean isFinished() {
        return finished.get();
    }

    /**
     * 取出至多 {@code maxLines} 行待上报日志（仅由 Worker SDK 调用）。
     *
     * @param maxLines 本次最多取出的行数
     * @return 日志行列表，无日志时返回空列表
     */
    List<String> drainLogLines(int maxLines) {
        List<String> lines = new ArrayList<>(Math.clamp(maxLines, 1, 128));
        int dropped = droppedLogLines.getAndSet(0);
        if (dropped > 0) {
            lines.add(LOG_PREFIX + "运行日志过多，已丢弃 " + dropped + " 行旧日志");
        }
        while (lines.size() < maxLines) {
            String line = logLines.poll();
            if (line == null) {
                break;
            }
            lines.add(line);
        }
        return lines;
    }

    /**
     * 把日志输出流中尚未换行的残余内容转成一行日志（仅由 Worker SDK 在任务结束时调用）。
     */
    void flushPendingLogLine() {
        synchronized (pendingLine) {
            if (!pendingLine.isEmpty()) {
                enqueueLine(stripCarriageReturn(pendingLine.toString()));
                pendingLine.setLength(0);
            }
        }
    }

    /**
     * 把一行日志放入待上报缓冲，超过上限时丢弃最旧的行。
     *
     * @param line 日志行
     */
    private void enqueueLine(String line) {
        while (logLines.size() >= MAX_BUFFERED_LOG_LINES) {
            if (logLines.poll() == null) {
                break;
            }
            droppedLogLines.incrementAndGet();
        }
        logLines.add(line);
    }

    /**
     * 执行单个回调，异常只记录日志不影响其他回调。
     *
     * @param callback 回调
     * @param action   回调类型（用于日志，如「中断」「挂起」）
     */
    private void runCallbackSafely(Runnable callback, String action) {
        try {
            callback.run();
        } catch (Throwable e) {
            log(LOG_PREFIX + action + "回调执行失败: " + describe(e));
            SfcTaskExecution.log.warn("{}任务[{}]{}回调执行失败", LOG_PREFIX, taskId, action, e);
        }
    }

    /**
     * 去掉 Windows 换行符残留的 {@code \r}。
     *
     * @param line 原始日志行
     * @return 处理后的日志行
     */
    private static String stripCarriageReturn(String line) {
        if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
            return line.substring(0, line.length() - 1);
        }
        return line;
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
     * 检查点同步保存器（由 Worker SDK 实现并绑定到执行上下文）。
     */
    @FunctionalInterface
    interface CheckpointSaver {

        /**
         * 同步保存一次检查点。
         *
         * @param execution  执行上下文
         * @param checkpoint 检查点内容
         * @return 是否已成功保存到 core
         */
        boolean save(SfcTaskExecution execution, String checkpoint);
    }

    /**
     * 按行切分的日志 Writer。
     */
    private final class LineBufferingWriter extends Writer {

        @Override
        public void write(char[] cbuf, int off, int len) {
            synchronized (pendingLine) {
                for (int i = off; i < off + len; i++) {
                    char c = cbuf[i];
                    if (c == '\n') {
                        enqueueLine(stripCarriageReturn(pendingLine.toString()));
                        pendingLine.setLength(0);
                    } else {
                        pendingLine.append(c);
                    }
                }
            }
        }

        @Override
        public void flush() {
            synchronized (pendingLine) {
                if (!pendingLine.isEmpty()) {
                    enqueueLine(stripCarriageReturn(pendingLine.toString()));
                    pendingLine.setLength(0);
                }
            }
        }

        @Override
        public void close() {
            flush();
        }
    }
}
