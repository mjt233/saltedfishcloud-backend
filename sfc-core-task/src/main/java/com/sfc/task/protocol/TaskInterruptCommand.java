package com.sfc.task.protocol;

/**
 * 任务中断指令。
 * <p>由 core 向微服务 worker 的回调地址发起，要求执行者尽快中断指定任务的执行。</p>
 *
 * @param taskId 要中断的任务id
 */
public record TaskInterruptCommand(Long taskId) {
}
