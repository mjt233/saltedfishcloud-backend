package com.sfc.cloud.task.worker;

import com.sfc.cloud.task.worker.protocol.TaskAbandonParam;
import com.sfc.cloud.task.worker.protocol.TaskCheckpointSaveParam;
import com.sfc.cloud.task.worker.protocol.TaskDispatchInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SfcTaskReporter} 的单元测试：挂起语义的放弃上报、检查点上报与 tick 补发。
 */
class SfcTaskReporterTest {

    private SfcTaskWorkerRegistry registry;

    private SfcTaskWorkerClient client;

    private SfcTaskReporter reporter;

    @BeforeEach
    void setUp() {
        registry = mock(SfcTaskWorkerRegistry.class);
        client = mock(SfcTaskWorkerClient.class);
        SfcTaskWorkerIdentity identity = mock(SfcTaskWorkerIdentity.class);
        when(identity.getWorkerId()).thenReturn("127.0.0.1:9000");
        reporter = new SfcTaskReporter(registry, client, identity);
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
     * 验证已收到挂起指令的任务按 detached=true 上报放弃执行权。
     */
    @Test
    void testReportAbandonDetachedParam() {
        SfcTaskExecution execution = execution();
        execution.requestDetach();

        reporter.reportAbandon(execution, "执行者停机，任务已挂起，等待接管");

        ArgumentCaptor<TaskAbandonParam> captor = ArgumentCaptor.forClass(TaskAbandonParam.class);
        verify(client).reportAbandon(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().detached());
        assertEquals("执行者停机，任务已挂起，等待接管", captor.getValue().message());
        assertEquals(1L, captor.getValue().taskId());
        assertEquals("127.0.0.1:9000", captor.getValue().workerId());
    }

    /**
     * 验证普通任务的放弃上报 detached 为 false。
     */
    @Test
    void testReportAbandonNormalParam() {
        SfcTaskExecution execution = execution();

        reporter.reportAbandon(execution, "执行者停机，放弃任务执行权");

        ArgumentCaptor<TaskAbandonParam> captor = ArgumentCaptor.forClass(TaskAbandonParam.class);
        verify(client).reportAbandon(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().detached());
    }

    /**
     * 验证同一个任务只会上报一次放弃。
     */
    @Test
    void testReportAbandonOnlyOnce() {
        SfcTaskExecution execution = execution();

        reporter.reportAbandon(execution, "第一次");
        reporter.reportAbandon(execution, "第二次");

        verify(client, times(1)).reportAbandon(any());
    }

    /**
     * 验证检查点上报携带完整协议字段，成功后不留下待重发内容。
     */
    @Test
    void testSaveCheckpointSuccess() {
        when(client.saveCheckpoint(any())).thenReturn(true);
        SfcTaskExecution execution = execution();

        assertTrue(reporter.saveCheckpoint(execution, "{\"phase\":\"DOWNLOADING\"}"));

        ArgumentCaptor<TaskCheckpointSaveParam> captor = ArgumentCaptor.forClass(TaskCheckpointSaveParam.class);
        verify(client).saveCheckpoint(captor.capture());
        assertEquals(1L, captor.getValue().taskId());
        assertEquals("127.0.0.1:9000", captor.getValue().workerId());
        assertEquals("test-type", captor.getValue().taskType());
        assertEquals("{\"phase\":\"DOWNLOADING\"}", captor.getValue().checkpoint());
        assertNull(execution.getPendingCheckpoint());
    }

    /**
     * 验证检查点上报失败不抛异常，并把内容记为待重发。
     */
    @Test
    void testSaveCheckpointFailureMarksPendingWithoutThrowing() {
        when(client.saveCheckpoint(any())).thenThrow(new IllegalStateException("模拟网络失败"));
        SfcTaskExecution execution = execution();

        boolean result = reporter.saveCheckpoint(execution, "cp-pending");

        assertFalse(result);
        assertEquals("cp-pending", execution.getPendingCheckpoint());
    }

    /**
     * 验证 tick 会补发待重发的检查点并在成功后清除待重发状态。
     */
    @Test
    void testTickFlushesPendingCheckpoint() {
        SfcTaskExecution execution = execution();
        when(registry.getRunningExecutions()).thenReturn(List.of(execution));
        when(client.saveCheckpoint(any())).thenReturn(true);
        execution.markPendingCheckpoint("cp-pending");

        reporter.tick();

        ArgumentCaptor<TaskCheckpointSaveParam> captor = ArgumentCaptor.forClass(TaskCheckpointSaveParam.class);
        verify(client).saveCheckpoint(captor.capture());
        assertEquals("cp-pending", captor.getValue().checkpoint());
        assertNull(execution.getPendingCheckpoint());
    }
}
