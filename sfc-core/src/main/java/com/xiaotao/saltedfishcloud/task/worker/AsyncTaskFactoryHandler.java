package com.xiaotao.saltedfishcloud.task.worker;

import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.cloud.task.worker.SfcTaskHandler;
import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.task.AsyncTask;
import com.sfc.task.AsyncTaskFactory;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

/**
 * {@link AsyncTaskFactory} 到 Worker SDK {@link SfcTaskHandler} 的桥接处理器。
 *
 * <p>每个 {@link AsyncTaskFactory} Bean 对应一个本处理器实例，负责把 SDK 下发的执行上下文转换为
 * sfc-core 原有的任务执行语义：</p>
 * <ul>
 *     <li>用执行上下文中的任务信息构造 {@link AsyncTaskRecord}，再交由任务工厂创建 {@link AsyncTask}；</li>
 *     <li>执行前把任务创建者绑定到当前线程的安全上下文（与历史本地执行器的行为一致），执行结束解绑；</li>
 *     <li>把 SDK 的中断指令转成 {@link AsyncTask#interrupt()} 调用；</li>
 *     <li>把 {@link AsyncTask#getProgress()} 的完整进度快照提供给 SDK 上报；</li>
 *     <li>把任务写入的日志按行转交给 SDK 的运行日志通道。</li>
 * </ul>
 *
 * <p>任务因中断而抛出异常时统一转换为 {@link SfcTaskInterruptedException}，使控制面把该任务记为
 * {@code CANCEL} 而不是 {@code FAILED}；其他异常原样向上抛出，由 SDK 上报为失败。</p>
 */
@Slf4j
public class AsyncTaskFactoryHandler implements SfcTaskHandler {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 任务工厂：负责按任务参数创建可执行的任务实例
     */
    private final AsyncTaskFactory factory;

    /**
     * 用户服务：用于在任务执行前绑定任务创建者的安全上下文
     */
    private final UserService userService;

    /**
     * 构造桥接处理器。
     *
     * @param factory     任务工厂
     * @param userService 用户服务
     */
    public AsyncTaskFactoryHandler(AsyncTaskFactory factory, UserService userService) {
        this.factory = factory;
        this.userService = userService;
    }

    /**
     * 获取本处理器支持的任务类型，直接取任务工厂声明的任务类型。
     *
     * @return 任务类型标识
     */
    @Override
    public String taskType() {
        return factory.getTaskType();
    }

    /**
     * 执行一个任务。
     *
     * <p>执行流程：构造任务记录并创建任务实例 → 绑定任务创建者的安全上下文 → 注册中断回调与进度快照
     * 提供者 → 执行任务并把日志按行写入 SDK 运行日志 → 解绑安全上下文。</p>
     *
     * @param execution SDK 下发的任务执行上下文
     * @throws SfcTaskInterruptedException 任务执行期间已收到中断指令时抛出，控制面据此记为取消
     * @throws Exception                  任务创建或执行失败时原样抛出，由 SDK 上报为失败
     */
    @Override
    public void execute(SfcTaskExecution execution) throws Exception {
        try {
            AsyncTask task = factory.createTask(execution.getParams(), buildRecord(execution));

            // 与历史本地执行器一致：把任务创建者作为当前操作用户绑定到执行线程
            SecureUtils.bindUser(UserPrincipal.from(userService.getUserById(execution.getUid())));
            execution.onInterrupt(task::interrupt);
            execution.setProgressSource(() -> progressMap(task));

            // 任务在开始执行前就已收到中断指令（排队期间被取消/停机放弃）：不再启动任务，直接按取消结束。
            // 否则 AsyncTask.interrupt() 在 execute() 尚未开始时会成为空操作，任务会一直跑到结束才被记为取消。
            if (execution.isInterruptRequested()) {
                throw new SfcTaskInterruptedException(
                        "任务[" + execution.getTaskId() + "]在执行前已收到中断指令");
            }

            try (TaskLogOutputStream logOutputStream = new TaskLogOutputStream(execution)) {
                task.execute(logOutputStream);
            }
        } catch (RuntimeException | Error e) {
            if (execution.isInterruptRequested()) {
                throw new SfcTaskInterruptedException(
                        "任务[" + execution.getTaskId() + "]收到中断指令后结束", e);
            }
            throw e;
        } finally {
            SecureUtils.unbind();
        }
    }

    /**
     * 由执行上下文构造任务记录。
     *
     * <p>任务执行只依赖 id、名称、类型、参数与创建者，{@code cpuOverhead} 只影响调度侧的并发控制，
     * 不参与执行，故不填充。</p>
     *
     * @param execution SDK 下发的任务执行上下文
     * @return 供任务工厂使用的任务记录
     */
    private AsyncTaskRecord buildRecord(SfcTaskExecution execution) {
        AsyncTaskRecord record = new AsyncTaskRecord();
        record.setId(execution.getTaskId());
        record.setName(execution.getTaskName());
        record.setTaskType(execution.getTaskType());
        record.setParams(execution.getParams());
        record.setUid(execution.getUid());
        return record;
    }

    /**
     * 把任务的进度对象转换为进度快照 Map，供 SDK 上报 loaded / total 与扩展字段。
     *
     * @param task 正在执行的任务
     * @return 进度快照；进度为 null 或转换失败时返回空 Map
     */
    private Map<String, Object> progressMap(AsyncTask task) {
        try {
            ProgressRecord progress = task.getProgress();
            if (progress == null) {
                return Collections.emptyMap();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshot = SfcMapperHolder.mapper.convertValue(progress, Map.class);
            return snapshot == null ? Collections.emptyMap() : snapshot;
        } catch (Throwable e) {
            log.warn("{}任务进度快照转换失败，本次上报按空进度处理", LOG_PREFIX, e);
            return Collections.emptyMap();
        }
    }
}
