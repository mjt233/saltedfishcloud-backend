package com.sfc.cloud.task.worker;

import com.sfc.cloud.task.worker.protocol.TaskDispatchInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SfcTaskExecutionRunner} 的单元测试：重点验证「挂起不报 FINISH」与常规结束状态上报。
 */
class SfcTaskExecutionRunnerTest {

    /**
     * 挂起任务的放弃说明，与 {@link SfcTaskExecutionRunner} 内的常量保持一致
     */
    private static final String DETACH_MESSAGE = "执行者停机，任务已挂起，等待接管";

    private SfcTaskWorkerRegistry registry;

    private SfcTaskReporter reporter;

    private SfcTaskExecutionRunner runner;

    @BeforeEach
    void setUp() {
        registry = mock(SfcTaskWorkerRegistry.class);
        reporter = mock(SfcTaskReporter.class);
        runner = new SfcTaskExecutionRunner(registry, reporter);
        when(reporter.reportStart(any())).thenReturn(true);
    }

    /**
     * 构造一个执行上下文。
     *
     * @return 执行上下文
     */
    private static SfcTaskExecution execution() {
        TaskDispatchInfo info = new TaskDispatchInfo(1L, "测试任务", "test-type", "{}", 1L, 10, 0, new Date(), null);
        return new SfcTaskExecution(info, "127.0.0.1:9000", (e, cp) -> true);
    }

    /**
     * 构造一个只实现必要方法的处理器。
     *
     * @param action 执行动作
     * @return 任务处理器
     */
    private static SfcTaskHandler handler(ThrowingConsumer action) {
        return new SfcTaskHandler() {
            @Override
            public String taskType() {
                return "test-type";
            }

            @Override
            public void execute(SfcTaskExecution execution) throws Exception {
                action.accept(execution);
            }

            @Override
            public boolean supportsDetach() {
                return true;
            }
        };
    }

    /**
     * 验证普通任务正常结束会上报 FINISH。
     */
    @Test
    void testNormalExecutionReportsFinish() {
        SfcTaskExecution execution = execution();

        runner.run(execution, handler(e -> {
        }));

        verify(reporter).reportFinish(execution, 2, "执行完成");
        verify(reporter, never()).reportAbandon(any(), anyString());
        verify(registry).unregisterExecution(1L);
    }

    /**
     * 验证任务正常返回但已收到挂起指令时按挂起上报放弃，不报 FINISH。
     */
    @Test
    void testDetachRequestedDoesNotReportFinish() {
        SfcTaskExecution execution = execution();

        runner.run(execution, handler(SfcTaskExecution::requestDetach));

        verify(reporter).reportAbandon(eq(execution), eq(DETACH_MESSAGE));
        verify(reporter, never()).reportFinish(any(), anyInt(), anyString());
        verify(registry).unregisterExecution(1L);
    }

    /**
     * 验证任务抛出 {@link SfcTaskDetachedException} 时按挂起处理，不报 FINISH。
     */
    @Test
    void testDetachedExceptionDoesNotReportFinish() {
        SfcTaskExecution execution = execution();

        runner.run(execution, handler(e -> {
            e.requestDetach();
            throw new SfcTaskDetachedException("任务实现检测到挂起，提前退出");
        }));

        verify(reporter).reportAbandon(eq(execution), eq(DETACH_MESSAGE));
        verify(reporter, never()).reportFinish(any(), anyInt(), anyString());
    }

    /**
     * 验证收到中断指令的任务按 CANCEL 结束。
     */
    @Test
    void testInterruptedExecutionReportsCancel() {
        SfcTaskExecution execution = execution();

        runner.run(execution, handler(SfcTaskExecution::requestInterrupt));

        verify(reporter).reportFinish(execution, 4, "任务收到中断指令，按取消结束");
        verify(reporter, never()).reportAbandon(any(), anyString());
    }

    /**
     * 验证处理器抛出异常时上报 FAILED 并携带异常信息。
     */
    @Test
    void testFailedExecutionReportsFailed() {
        SfcTaskExecution execution = execution();

        runner.run(execution, handler(e -> {
            throw new IllegalStateException("模拟失败");
        }));

        verify(reporter).reportFinish(execution, 3, "任务执行失败: 模拟失败");
        verify(reporter, never()).reportAbandon(any(), anyString());
    }

    /**
     * 验证开始上报被拒绝时跳过执行，且不写终态。
     */
    @Test
    void testStartRejectedSkipsExecution() {
        when(reporter.reportStart(any())).thenReturn(false);
        SfcTaskExecution execution = execution();
        SfcTaskHandler handler = mock(SfcTaskHandler.class);

        runner.run(execution, handler);

        verifyNoInteractions(handler);
        verify(reporter, never()).reportFinish(any(), anyInt(), anyString());
        verify(reporter, never()).reportAbandon(any(), anyString());
        verify(registry).unregisterExecution(1L);
    }

    /**
     * 可抛出异常的执行动作。
     */
    @FunctionalInterface
    private interface ThrowingConsumer {

        /**
         * 执行动作。
         *
         * @param execution 执行上下文
         * @throws Exception 执行失败时抛出
         */
        void accept(SfcTaskExecution execution) throws Exception;
    }
}
