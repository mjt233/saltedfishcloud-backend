package com.sfc.cloud.task.worker;

import java.io.Serial;

/**
 * 任务挂起异常。
 *
 * <p>可挂起任务（{@link SfcTaskHandler#supportsDetach()} 返回 true）在检测到执行者已请求挂起
 * （{@link SfcTaskExecution#isDetachRequested()}）后，可抛出本异常（也可以正常返回）提前结束本次执行。
 * Worker SDK 捕获本异常后<b>不会上报 FINISH</b>，而是按「已挂起」上报放弃执行权：任务回到等待中，
 * 保留检查点并交由其他执行者接管。</p>
 */
public class SfcTaskDetachedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 3427456417848891628L;

    /**
     * 以挂起说明构造异常。
     *
     * @param message 挂起说明
     */
    public SfcTaskDetachedException(String message) {
        super(message);
    }

    /**
     * 以挂起说明与原因构造异常。
     *
     * @param message 挂起说明
     * @param cause   触发挂起的原因
     */
    public SfcTaskDetachedException(String message, Throwable cause) {
        super(message, cause);
    }
}
