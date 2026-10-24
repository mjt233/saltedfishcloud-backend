package com.sfc.cloud.task.worker;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行器：在线程池线程中执行单个已认领任务，并负责开始/结束上报与资源清理。
 *
 * <p>结束状态与 core 的 {@code AsyncTaskConstants.Status} 保持一致：
 * {@code 2=FINISH}、{@code 3=FAILED}、{@code 4=CANCEL}。</p>
 */
@Slf4j
class SfcTaskExecutionRunner {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 任务完成状态，与 core 的 FINISH 一致
     */
    private static final int STATUS_FINISH = 2;

    /**
     * 任务失败状态，与 core 的 FAILED 一致
     */
    private static final int STATUS_FAILED = 3;

    /**
     * 任务取消状态，与 core 的 CANCEL 一致
     */
    private static final int STATUS_CANCEL = 4;

    /**
     * 运行中任务注册表
     */
    private final SfcTaskWorkerRegistry registry;

    /**
     * 执行上报器
     */
    private final SfcTaskReporter reporter;

    /**
     * 构造任务执行器（由自动装配创建）。
     *
     * @param registry 运行中任务注册表
     * @param reporter 执行上报器
     */
    SfcTaskExecutionRunner(SfcTaskWorkerRegistry registry, SfcTaskReporter reporter) {
        this.registry = registry;
        this.reporter = reporter;
    }

    /**
     * 执行一个已认领的任务。
     *
     * @param execution 已登记的执行上下文
     * @param handler   任务处理器
     */
    void run(SfcTaskExecution execution, SfcTaskHandler handler) {
        Long taskId = execution.getTaskId();
        if (execution.isFinished()) {
            // 停机放弃流程可能已把排队中的任务标记结束
            registry.unregisterExecution(taskId);
            log.info("{}任务[{}]开始执行前已被放弃/取消，跳过执行", LOG_PREFIX, taskId);
            return;
        }
        int status = STATUS_FINISH;
        String message = "执行完成";
        boolean accepted = false;
        try {
            accepted = reporter.reportStart(execution);
            if (!accepted) {
                log.warn("{}任务 {}-{} 已不在运行中（可能已被取消），跳过执行",
                        LOG_PREFIX, execution.getTaskName(), taskId);
                return;
            }
            execution.log("任务开始执行：name=" + execution.getTaskName()
                    + ", taskType=" + execution.getTaskType() + ", uid=" + execution.getUid());
            handler.execute(execution);
            if (execution.isInterruptRequested()) {
                status = STATUS_CANCEL;
                message = "任务收到中断指令，按取消结束";
            }
            execution.log(LOG_PREFIX + message);
        } catch (SfcTaskInterruptedException e) {
            status = STATUS_CANCEL;
            message = messageOf(e, "任务被中断");
            execution.log(LOG_PREFIX + message);
        } catch (Throwable e) {
            status = STATUS_FAILED;
            message = "任务执行失败: " + describe(e);
            execution.log(LOG_PREFIX + message);
            log.error("{}任务 {}-{} 执行失败", LOG_PREFIX, execution.getTaskName(), taskId, e);
        } finally {
            // 先从注册表移除：避免结束上报期间仍有中断指令命中本执行上下文，向即将复用的线程池线程发中断
            registry.unregisterExecution(taskId);
            // 清除可能由中断回调设置到执行线程上的中断标记，避免污染线程池中的下一个任务
            if (Thread.interrupted()) {
                log.debug("{}任务[{}]结束后清除了晚到的中断标记", LOG_PREFIX, taskId);
            }
            try {
                if (accepted) {
                    reporter.reportFinish(execution, status, message);
                }
            } catch (Throwable e) {
                log.warn("{}任务 {}-{} 结束上报异常: {}", LOG_PREFIX, execution.getTaskName(), taskId,
                        describe(e));
            }
        }
    }

    /**
     * 取异常消息；消息为空时使用兜底说明。
     *
     * @param e          异常
     * @param defaultMsg 兜底说明
     * @return 结束说明
     */
    private static String messageOf(Throwable e, String defaultMsg) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? defaultMsg : message;
    }

    /**
     * 异常简要描述：优先使用异常消息，消息为空时回退异常类型名。
     *
     * @param e 异常
     * @return 简要描述
     */
    private static String describe(Throwable e) {
        return messageOf(e, e.getClass().getSimpleName());
    }
}
