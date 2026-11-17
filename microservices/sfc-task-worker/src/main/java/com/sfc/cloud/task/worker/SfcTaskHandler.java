package com.sfc.cloud.task.worker;

/**
 * 任务处理器。
 *
 * <p>实现本接口并注册为 Spring Bean 后，Worker SDK 会按 {@link #taskType()} 认领对应类型的任务，
 * 并调用 {@link #execute(SfcTaskExecution)} 执行。宿主（sfc-core 内嵌执行者）与微服务使用同一套
 * 处理器契约。</p>
 *
 * <p>实现约定：</p>
 * <ul>
 *     <li>任务失败请直接抛出异常，SDK 会上报 FAILED 并携带异常信息与尾部日志；</li>
 *     <li>长耗时逻辑应周期性调用 {@link SfcTaskExecution#checkInterrupted()}（或通过
 *         {@link SfcTaskExecution#onInterrupt(Runnable)} 注册中断回调）以响应 core 下发的中断指令；</li>
 *     <li>日志请写入 {@link SfcTaskExecution#getLogWriter()} 或调用 {@link SfcTaskExecution#log(String)}；</li>
 *     <li>进度请通过 {@link SfcTaskExecution#getProgress()} 更新，SDK 会按约 1 秒的节流周期上报；</li>
 *     <li>可挂起任务（{@link #supportsDetach()} 返回 true）应注册
 *         {@link SfcTaskExecution#onDetach(Runnable)} 保存检查点，并在检测到
 *         {@link SfcTaskExecution#isDetachRequested()} 后退出本次执行。</li>
 * </ul>
 */
public interface SfcTaskHandler {

    /**
     * 本处理器支持的任务类型。
     *
     * <p>必须与任务提交时的 {@code taskType} 完全一致；同一服务内不允许出现重复的任务类型。</p>
     *
     * @return 任务类型标识
     */
    String taskType();

    /**
     * 执行一个任务。
     *
     * @param execution 任务执行上下文，提供任务信息、日志输出流、进度对象与中断协作能力
     * @throws Exception 任务执行失败时抛出，SDK 会捕获并上报 FAILED
     */
    void execute(SfcTaskExecution execution) throws Exception;

    /**
     * 是否支持「挂起」（detach）。
     *
     * <p>返回 true 表示本处理器能感知并保留外部工作现场（例如交给外部下载器的长任务）：执行者停机时
     * SDK 不会强制中断任务，而是通过 {@link SfcTaskExecution#onDetach(Runnable)} 通知任务实现保存检查点
     * （{@link SfcTaskExecution#saveCheckpoint(String)}）后退出本次执行，任务随后回到等待中，
     * 由其他执行者带着该检查点接管。</p>
     *
     * <p>默认 false，既有实现无需迁移。启用后可配合抛出 {@link SfcTaskDetachedException} 提前结束执行。</p>
     *
     * @return 支持挂起返回 true
     */
    default boolean supportsDetach() {
        return false;
    }
}
