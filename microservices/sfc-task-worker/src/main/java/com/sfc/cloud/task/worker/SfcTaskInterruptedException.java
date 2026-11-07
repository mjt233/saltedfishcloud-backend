package com.sfc.cloud.task.worker;

import java.io.Serial;

/**
 * 任务被中断异常。
 *
 * <p>由 {@link SfcTaskExecution#checkInterrupted()} 在任务已被 core 下发中断指令时抛出，
 * 任务处理器也可以主动抛出本异常表示「因中断而提前结束」。Worker SDK 捕获本异常后会把任务
 * 结果上报为 {@code CANCEL}（而不是 FAILED）。</p>
 *
 * <p>建议处理器捕获 {@link InterruptedException}（例如 {@code Thread.sleep} 被打断）后，
 * 转换为本异常抛出，以保持中断语义的一致性。</p>
 */
public class SfcTaskInterruptedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -3325815963224308801L;

    /**
     * 以中断说明构造异常。
     *
     * @param message 中断说明
     */
    public SfcTaskInterruptedException(String message) {
        super(message);
    }

    /**
     * 以中断说明与原因构造异常。
     *
     * @param message 中断说明
     * @param cause   触发中断的原因
     */
    public SfcTaskInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
