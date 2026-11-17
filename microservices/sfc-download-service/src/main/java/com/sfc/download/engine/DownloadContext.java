package com.sfc.download.engine;

import com.sfc.cloud.task.worker.SfcTaskDetachedException;
import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.p2p.P2pTaskState;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.util.DownloadFileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 下载引擎的执行上下文。
 *
 * <p>一次任务执行对应一个实例（由任务处理器创建后传给引擎，引擎只读取/使用，不得跨任务复用），
 * 向引擎暴露：任务参数、进度与日志输出、任务临时目录（创建策略由任务处理器按下载类型注入，见
 * {@link DownloadTempDirCreator}），以及中断协作能力。</p>
 *
 * <p><b>中断语义：</b>任务处理器把 Worker SDK 的中断指令转发到 {@link #cancel()}；已注册的取消回调
 * （关闭下载流、终止外部进程任务等）会被立即执行，且 {@link #isCancelled()} 变为 {@code true}。
 * 引擎在阻塞操作之外应周期性调用 {@link #checkInterrupted()}，以便及时以「取消」语义退出。</p>
 *
 * <p><b>任务展示名：</b>引擎在能确定下载文件名时（HTTP 响应头 / 地址、aria2 文件清单），可通过
 * {@link #updateTaskName(String)} 上报任务展示名，使用户在任务运行期间即可在列表中看到名称；
 * 重复上报相同名称会被忽略，上报失败不影响下载本身。</p>
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
     * 任务展示名上报回调（由任务处理器在创建上下文时注入，用于把引擎解析出的名称回写下载任务记录）。
     */
    private final Consumer<String> taskNameReporter;

    /**
     * 任务临时目录创建策略（由任务处理器按下载类型选择；为 {@code null} 时使用默认策略）。
     *
     * <p>HTTP / Git 等类型使用默认策略（本服务 JVM 临时目录）；磁力链 / 种子可注入 aria2 共享工作区
     * 策略，使 aria2 容器写入的任务目录对本服务可见。</p>
     */
    private final DownloadTempDirCreator tempDirCreator;

    /**
     * 最近一次已上报的任务展示名，用于去重（避免轮询期间重复写库）。
     */
    private final AtomicReference<String> reportedTaskName = new AtomicReference<>();

    /**
     * 本次任务的临时目录（懒创建）。
     */
    private volatile Path tempDir;

    /**
     * P2P 任务的检查点镜像（由 {@link com.sfc.download.engine.p2p.P2pTaskCoordinator} 挂载）。
     *
     * <p>非 P2P 任务始终为 {@code null}；任务处理器在失败/取消收口时据此决定 aria2 任务结果与本地目录
     * 是否清理（生命周期收口矩阵）。</p>
     */
    private volatile P2pTaskState p2pState;

    /**
     * 构造执行上下文（不上报任务展示名，使用默认的临时目录策略）。
     *
     * @param execution Worker 的任务执行上下文，不能为 {@code null}
     * @param params    下载任务参数，不能为 {@code null}
     * @throws IllegalArgumentException 入参为 {@code null} 时抛出
     */
    public DownloadContext(SfcTaskExecution execution, DownloadTaskParams params) {
        this(execution, params, null, null);
    }

    /**
     * 构造执行上下文（使用默认的临时目录策略）。
     *
     * @param execution        Worker 的任务执行上下文，不能为 {@code null}
     * @param params           下载任务参数，不能为 {@code null}
     * @param taskNameReporter 任务展示名上报回调，为 {@code null} 表示不上报
     * @throws IllegalArgumentException 任务执行上下文或下载参数为 {@code null} 时抛出
     */
    public DownloadContext(SfcTaskExecution execution, DownloadTaskParams params, Consumer<String> taskNameReporter) {
        this(execution, params, taskNameReporter, null);
    }

    /**
     * 构造执行上下文（可指定任务展示名上报回调与临时目录策略）。
     *
     * @param execution        Worker 的任务执行上下文，不能为 {@code null}
     * @param params           下载任务参数，不能为 {@code null}
     * @param taskNameReporter 任务展示名上报回调，为 {@code null} 表示不上报
     * @param tempDirCreator   任务临时目录创建策略；为 {@code null} 时使用
     *                         {@link DownloadFileUtils#createTaskTempDir(String)} 作为默认策略
     * @throws IllegalArgumentException 任务执行上下文或下载参数为 {@code null} 时抛出
     */
    public DownloadContext(SfcTaskExecution execution, DownloadTaskParams params, Consumer<String> taskNameReporter,
                           DownloadTempDirCreator tempDirCreator) {
        if (execution == null) {
            throw new IllegalArgumentException("任务执行上下文不能为空");
        }
        if (params == null) {
            throw new IllegalArgumentException("下载任务参数不能为空");
        }
        this.execution = execution;
        this.params = params;
        this.taskNameReporter = taskNameReporter;
        this.tempDirCreator = tempDirCreator == null ? DownloadFileUtils::createTaskTempDir : tempDirCreator;
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
     * 获取本次任务的本地临时目录（首次调用时按注入的策略创建）。
     *
     * <p>未注入自定义策略时，目录位于
     * {@code ${java.io.tmpdir}/offline-download/<下载任务id>-<UUID>}；磁力链 / 种子任务可注入 aria2
     * 共享工作区策略，此时目录位于 {@code <sharedDir>/offline-download/<下载任务id>-<UUID>}。
     * 任务结束时由任务处理器递归删除；同一次执行内多次调用返回同一目录。</p>
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
                    tempDir = tempDirCreator.create(taskFlag());
                } catch (IOException e) {
                    throw new SfcJsonException(500, "创建下载临时目录失败: " + e.getMessage());
                }
            }
            return tempDir;
        }
    }

    /**
     * 采用一个已存在的任务目录（P2P 接管场景复用检查点记录的目录）。
     *
     * <p>正常情况下任务目录由 {@link #getTempDir()} 按注入策略懒创建；但 P2P 任务停机恢复时必须复用
     * 检查点中的同一个目录（配合 aria2 的 {@code continue=true} 才能续传），因此由 P2P 协调器在读取
     * 检查点后调用本方法把目录直接挂到上下文上。本方法<b>不创建</b>目录，目录是否存在由调用方负责
     * （续传场景会自行创建缺失目录）。</p>
     *
     * <p>一旦任务目录已创建，再次采用不同目录会抛业务异常——避免同一任务写入两个目录造成产物不一致。</p>
     *
     * @param existingDir 要采用的任务目录（会被归一化为绝对路径），不能为 {@code null}
     * @throws SfcJsonException 入参为 {@code null}，或与已创建的任务目录不一致时抛出（HTTP 500 语义）
     */
    public void useTempDir(Path existingDir) {
        if (existingDir == null) {
            throw new SfcJsonException(500, "复用任务目录失败: 目录不能为空");
        }
        Path normalized = existingDir.toAbsolutePath().normalize();
        synchronized (this) {
            if (tempDir == null) {
                this.tempDir = normalized;
                return;
            }
            if (!tempDir.equals(normalized)) {
                throw new SfcJsonException(500,
                        "复用任务目录失败: 任务目录已创建且不一致（existing=" + tempDir + ", requested=" + normalized + "）");
            }
        }
    }

    /**
     * 获取认领时下发的检查点（之后为最近一次保存的内容）。
     *
     * <p>检查点的语义由任务实现自定义：P2P 任务存放 {@code P2pTaskState} JSON；其它下载类型不使用。</p>
     *
     * @return 检查点文本；没有检查点时返回 {@code null}
     */
    public String getCheckpoint() {
        return execution.getCheckpoint();
    }

    /**
     * 保存任务检查点。
     *
     * <p>同步上报 core；失败不抛异常（SDK 会记录待补发并在周期上报中重试），调用方无需重试。</p>
     *
     * @param checkpoint 检查点文本（JSON），空白值视为不保存
     * @return 本次是否已同步到 core
     */
    public boolean saveCheckpoint(String checkpoint) {
        return execution.saveCheckpoint(checkpoint);
    }

    /**
     * 判断执行者是否已请求挂起（停机）。
     *
     * <p>可挂起任务（{@code SfcTaskHandler#supportsDetach()} 返回 {@code true}）在耗时循环中应周期性
     * 检查本方法，检测到后保存检查点并抛 {@link SfcTaskDetachedException} 退出，等待其它实例接管。</p>
     *
     * @return 已收到挂起请求返回 {@code true}
     */
    public boolean isDetachRequested() {
        return execution.isDetachRequested();
    }

    /**
     * 检查执行者是否已请求挂起，已请求时抛出挂起异常。
     *
     * <p>抛出前不触发取消回调——挂起语义是「保留外部现场后退出」，与取消（中断并清理）不同。</p>
     *
     * @throws SfcTaskDetachedException 已收到挂起请求时抛出
     */
    public void checkDetach() {
        if (isDetachRequested()) {
            throw new SfcTaskDetachedException("执行者停机，任务已挂起并等待接管");
        }
    }

    /**
     * 获取本次 P2P 任务的检查点镜像。
     *
     * @return P2P 检查点；非 P2P 任务或尚未挂载时返回 {@code null}
     */
    public P2pTaskState getP2pState() {
        return p2pState;
    }

    /**
     * 挂载本次 P2P 任务的检查点镜像（仅由 P2P 协调器调用）。
     *
     * @param state P2P 检查点，允许为 {@code null}
     */
    public void attachP2pState(P2pTaskState state) {
        this.p2pState = state;
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
     * 上报任务展示名（可选能力）。
     *
     * <p>引擎在解析出下载文件名后调用，使任务在运行中即可展示名称。空白名称会被忽略；与上一次上报值
     * 相同时直接跳过（aria2 轮询会周期性拿到同一份文件清单）；回调异常只记任务日志，不影响下载本身。</p>
     *
     * @param name 任务展示名，允许为 {@code null} 或空白
     */
    public void updateTaskName(String name) {
        if (!SfcStringUtils.hasText(name)) {
            return;
        }
        Consumer<String> reporter = taskNameReporter;
        if (reporter == null) {
            return;
        }
        String normalized = name.trim();
        String previous = reportedTaskName.get();
        if (normalized.equals(previous)) {
            return;
        }
        if (!reportedTaskName.compareAndSet(previous, normalized)) {
            // 并发上报时以先写入的调用为准，避免重复写库
            return;
        }
        try {
            reporter.accept(normalized);
        } catch (RuntimeException e) {
            execution.log(DownloadTaskConstants.LOG_PREFIX + "回写任务名称失败: " + describe(e));
        }
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
