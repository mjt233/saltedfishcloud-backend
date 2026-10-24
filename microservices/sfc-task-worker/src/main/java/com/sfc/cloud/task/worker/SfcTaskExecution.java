package com.sfc.cloud.task.worker;

import com.sfc.cloud.task.worker.protocol.TaskDispatchInfo;
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
 *         {@link #checkInterrupted()} 在已中断时抛出 {@link SfcTaskInterruptedException}。</li>
 * </ul>
 *
 * <p>日志写入与中断回调注册均可在多线程场景下安全使用；日志行建议保持在单线程内顺序写入。</p>
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
    private final Long taskId;

    /**
     * 任务名称
     */
    private final String taskName;

    /**
     * 任务类型
     */
    private final String taskType;

    /**
     * 任务创建者用户id
     */
    private final Long uid;

    /**
     * 任务参数（JSON 或纯文本，由任务处理器自行解析）
     */
    private final String params;

    /**
     * 执行任务的 worker 标识
     */
    private final String workerId;

    /**
     * 进度快照
     */
    private final SfcTaskProgress progress = new SfcTaskProgress();

    /**
     * 完整进度快照提供者；未设置时为 null（按 {@link #progress} 上报）
     */
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
     */
    private final PrintWriter logWriter;

    /**
     * 尚未遇到换行符的日志片段
     */
    private final StringBuilder pendingLine = new StringBuilder();

    /**
     * 构造执行上下文（仅由 Worker SDK 调用）。
     *
     * @param dispatchInfo 下发的任务信息
     * @param workerId     执行者标识
     */
    SfcTaskExecution(TaskDispatchInfo dispatchInfo, String workerId) {
        this.taskId = dispatchInfo.id();
        this.taskName = dispatchInfo.name();
        this.taskType = dispatchInfo.taskType();
        this.uid = dispatchInfo.uid();
        this.params = dispatchInfo.params();
        this.workerId = workerId;
        this.logWriter = new PrintWriter(new LineBufferingWriter(), true);
    }

    /**
     * 获取任务id。
     *
     * @return 任务id
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * 获取任务名称。
     *
     * @return 任务名称
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * 获取任务类型。
     *
     * @return 任务类型
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * 获取任务创建者用户id。
     *
     * @return 用户id
     */
    public Long getUid() {
        return uid;
    }

    /**
     * 获取任务参数（JSON 或纯文本，由任务处理器自行解析）。
     *
     * @return 任务参数，可能为 null
     */
    public String getParams() {
        return params;
    }

    /**
     * 获取执行本任务的 worker 标识。
     *
     * @return worker 标识
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * 获取任务进度对象。
     *
     * @return 进度对象（loaded / total）
     */
    public SfcTaskProgress getProgress() {
        return progress;
    }

    /**
     * 设置完整进度快照提供者。
     *
     * <p>供内嵌执行者（如 sfc-core 本地任务）使用：进度快照以 {@code Map<String, Object>} 形式给出，
     * 其中键 {@code loaded} / {@code total} 为进度量，其余键（{@code speed}、{@code lastUpdateTime} 除外）
     * 作为进度扩展信息一并上报；SDK 会在每个上报周期重新读取快照。</p>
     *
     * <p>微服务任务处理器无需使用本方法，直接通过 {@link #getProgress()} 更新进度即可。</p>
     *
     * @param progressSource 进度快照提供者，返回 null 时回退到 {@link #getProgress()} 的进度值；
     *                       传 null 表示取消提供者
     */
    public void setProgressSource(Supplier<Map<String, Object>> progressSource) {
        this.progressSource = progressSource;
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
     * 获取日志输出流。
     *
     * <p>写入的内容会按行缓冲，由 SDK 定期（约 1 秒）批量上报给 core；任务结束时残余内容会随
     * 结束上报一并提交。{@link java.io.PrintWriter#println(String)} 会自动换行并触发刷新。</p>
     *
     * @return 日志输出流
     */
    public PrintWriter getLogWriter() {
        return logWriter;
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
            runCallbackSafely(callback);
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
            runCallbackSafely(callback);
        }
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
     * 执行单个中断回调，异常只记录日志不影响其他回调。
     *
     * @param callback 中断回调
     */
    private void runCallbackSafely(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable e) {
            log(LOG_PREFIX + "中断回调执行失败: " + describe(e));
            SfcTaskExecution.log.warn("{}任务[{}]中断回调执行失败", LOG_PREFIX, taskId, e);
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
