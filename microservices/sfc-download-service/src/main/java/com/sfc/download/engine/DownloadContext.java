package com.sfc.download.engine;

import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.util.DownloadFileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 下载引擎的执行上下文。
 *
 * <p>一次任务执行对应一个实例（由任务处理器创建后传给引擎，引擎只读取/使用，不得跨任务复用），
 * 向引擎暴露：任务参数、进度与日志输出、任务临时目录，以及中断协作能力。</p>
 *
 * <p><b>中断语义：</b>任务处理器把 Worker SDK 的中断指令转发到 {@link #cancel()}；已注册的取消回调
 * （关闭下载流、终止外部进程任务等）会被立即执行，且 {@link #isCancelled()} 变为 {@code true}。
 * 引擎在阻塞操作之外应周期性调用 {@link #checkInterrupted()}，以便及时以「取消」语义退出。</p>
 *
 * <p><b>线程安全性：</b>取消回调集合与取消标记均为并发安全实现，取消指令可能来自 Worker 的中断线程。</p>
 */
public class DownloadContext {

    /**
     * Worker 提供的任务执行上下文（进度、日志、中断状态）。
     */
    private final SfcTaskExecution execution;

    /**
     * 本次任务的下载参数。
     */
    private final DownloadTaskParams params;

    /**
     * 中断时需要执行的资源释放回调（关闭下载流、终止外部下载任务等）。
     */
    private final List<Runnable> cancellers = new CopyOnWriteArrayList<>();

    /**
     * 是否已触发取消（保证取消回调只执行一次）。
     */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * 本次任务的临时目录（懒创建）。
     */
    private volatile Path tempDir;

    /**
     * 构造执行上下文。
     *
     * @param execution Worker 的任务执行上下文，不能为 {@code null}
     * @param params    下载任务参数，不能为 {@code null}
     * @throws IllegalArgumentException 入参为 {@code null} 时抛出
     */
    public DownloadContext(SfcTaskExecution execution, DownloadTaskParams params) {
        if (execution == null) {
            throw new IllegalArgumentException("任务执行上下文不能为空");
        }
        if (params == null) {
            throw new IllegalArgumentException("下载任务参数不能为空");
        }
        this.execution = execution;
        this.params = params;
    }

    /**
     * 获取 Worker 的任务执行上下文（用于读写进度）。
     *
     * @return 任务执行上下文，永不为 {@code null}
     */
    public SfcTaskExecution getExecution() {
        return execution;
    }

    /**
     * 获取本次任务的下载参数。
     *
     * @return 下载参数，永不为 {@code null}
     */
    public DownloadTaskParams getParams() {
        return params;
    }

    /**
     * 获取下载任务主键。
     *
     * @return 下载任务 id；参数中未回填时为 {@code null}
     */
    public Long getDownloadId() {
        return params.downloadId;
    }

    /**
     * 获取本次任务的本地临时目录（首次调用时创建）。
     *
     * <p>目录位于 {@code ${java.io.tmpdir}/offline-download/<下载任务id>-<UUID>}，任务结束时由
     * 任务处理器递归删除；同一次执行内多次调用返回同一目录。</p>
     *
     * @return 已创建的任务临时目录
     * @throws SfcJsonException 目录创建失败时抛出（HTTP 500 语义）
     */
    public Path getTempDir() {
        Path current = tempDir;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (tempDir == null) {
                try {
                    tempDir = DownloadFileUtils.createTaskTempDir(taskFlag());
                } catch (IOException e) {
                    throw new SfcJsonException(500, "创建下载临时目录失败: " + e.getMessage());
                }
            }
            return tempDir;
        }
    }

    /**
     * 输出一行任务运行日志。
     *
     * <p>自动追加统一的 {@link DownloadTaskConstants#LOG_PREFIX} 前缀后，经
     * {@link SfcTaskExecution#log(String)} 上报到 core，前端任务详情可见。空白消息会被忽略。</p>
     *
     * @param message 日志内容
     */
    public void log(String message) {
        if (!SfcStringUtils.hasText(message)) {
            return;
        }
        execution.log(DownloadTaskConstants.LOG_PREFIX + message);
    }

    /**
     * 注册取消回调。
     *
     * <p>回调应快速返回且不得阻塞；同一个回调只应注册一次。若注册时任务已被取消，回调会被立即执行一次，
     * 避免出现「资源在取消之后才注册、导致无法释放」的问题。</p>
     *
     * @param canceller 取消回调，为 {@code null} 时忽略
     */
    public void registerCanceller(Runnable canceller) {
        if (canceller == null) {
            return;
        }
        cancellers.add(canceller);
        if (isCancelled()) {
            runQuietly(canceller);
        }
    }

    /**
     * 判断任务是否已被取消。
     *
     * @return 已取消（收到中断指令或已触发取消回调）返回 {@code true}
     */
    public boolean isCancelled() {
        return cancelled.get() || execution.isInterruptRequested();
    }

    /**
     * 检查任务是否已被取消，已取消时抛出中断异常。
     *
     * <p>抛出前会触发一次 {@link #cancel()}，保证已注册的资源释放回调被执行。</p>
     *
     * @throws SfcTaskInterruptedException 任务已被取消时抛出
     */
    public void checkInterrupted() {
        if (!isCancelled()) {
            return;
        }
        cancel();
        throw new SfcTaskInterruptedException("下载任务已被中断");
    }

    /**
     * 触发取消：幂等，依次执行全部已注册的取消回调。
     *
     * <p>通常由任务处理器注册到 Worker 的中断回调中（
     * {@code execution.onInterrupt(context::cancel)}），使阻塞在流读取或外部进程上的执行逻辑能够尽快退出。</p>
     */
    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        if (cancellers.isEmpty()) {
            return;
        }
        for (Runnable canceller : cancellers) {
            runQuietly(canceller);
        }
        cancellers.clear();
    }

    /**
     * 执行单个取消回调，异常只记录到任务日志，不中断其它回调的执行。
     *
     * @param canceller 取消回调
     */
    private void runQuietly(Runnable canceller) {
        try {
            canceller.run();
        } catch (RuntimeException e) {
            execution.log(DownloadTaskConstants.LOG_PREFIX + "执行下载取消回调失败: " + describe(e));
        }
    }

    /**
     * 获取用于临时目录命名的任务标识（下载任务 id，缺失时使用 {@code task}）。
     *
     * @return 任务标识
     */
    private String taskFlag() {
        Long downloadId = params.downloadId;
        return downloadId == null ? "task" : String.valueOf(downloadId);
    }

    /**
     * 把异常转换为可读的简短描述。
     *
     * @param e 异常，允许为 {@code null}
     * @return 异常描述，永不为 {@code null}
     */
    private static String describe(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        String message = e.getMessage();
        return SfcStringUtils.hasText(message) ? message : e.getClass().getSimpleName();
    }
}
