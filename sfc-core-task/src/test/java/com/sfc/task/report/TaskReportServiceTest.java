package com.sfc.task.report;

import com.sfc.cloud.task.worker.protocol.TaskAbandonParam;
import com.sfc.cloud.task.worker.protocol.TaskFinishReportParam;
import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.constants.TaskMQTopic;
import com.sfc.task.model.AsyncTaskLogRecord;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskLogRecordRepo;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.task.schedule.TaskScheduleConstants;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TaskReportService} 的单元测试：挂起（detached）放弃、故障转移回收与终态清理检查点。
 */
class TaskReportServiceTest {

    private AsyncTaskRecordRepo taskRecordRepo;

    private AsyncTaskLogRecordRepo logRecordRepo;

    private MQService mqService;

    private TaskCheckpointService checkpointService;

    private TaskReportService service;

    @BeforeEach
    void setUp() {
        taskRecordRepo = mock(AsyncTaskRecordRepo.class);
        logRecordRepo = mock(AsyncTaskLogRecordRepo.class);
        TaskLogBufferService logBufferService = mock(TaskLogBufferService.class);
        mqService = mock(MQService.class);
        CacheService cacheService = mock(CacheService.class);
        checkpointService = mock(TaskCheckpointService.class);
        service = new TaskReportService(taskRecordRepo, logRecordRepo, logBufferService, mqService,
                cacheService, checkpointService);
        when(logBufferService.drain(anyLong())).thenReturn(List.of());
    }

    /**
     * 构造一条运行中的任务记录。
     *
     * @param taskId 任务id
     * @return 任务记录
     */
    private static AsyncTaskRecord runningRecord(Long taskId) {
        AsyncTaskRecord record = new AsyncTaskRecord();
        record.setId(taskId);
        record.setName("任务" + taskId);
        record.setTaskType("test-type");
        record.setStatus(AsyncTaskConstants.Status.RUNNING);
        record.setClaimedService("sfc-core");
        record.setClaimedWorker("127.0.0.1:9000");
        record.setRetryCount(0);
        record.setIsTemp(false);
        record.setUid(1L);
        return record;
    }

    /**
     * 验证 detached=true 的放弃上报走「不消耗重试次数」的重新排队，且保留检查点。
     */
    @Test
    void testReportAbandonDetachedRequeuesWithoutRetry() {
        AsyncTaskRecord record = runningRecord(10L);
        when(taskRecordRepo.findById(10L)).thenReturn(Optional.of(record));
        when(taskRecordRepo.requeueDetached(10L)).thenReturn(1);

        boolean result = service.reportAbandon(new TaskAbandonParam(10L, "127.0.0.1:9000", "停机挂起", true));

        assertTrue(result);
        verify(taskRecordRepo).requeueDetached(10L);
        verify(taskRecordRepo, never()).abandonAndRequeue(anyLong(), anyInt());
        verify(mqService).sendBroadcast(eq(TaskMQTopic.TASK_PUBLISH), eq(10L));
        verify(checkpointService, never()).delete(any());
        verify(logRecordRepo).save(any(AsyncTaskLogRecord.class));
    }

    /**
     * 验证普通放弃上报（detached 为空）仍按故障转移规则累加重试次数。
     */
    @Test
    void testReportAbandonNormalRequeuesWithRetry() {
        AsyncTaskRecord record = runningRecord(10L);
        when(taskRecordRepo.findById(10L)).thenReturn(Optional.of(record));
        when(taskRecordRepo.abandonAndRequeue(10L, TaskScheduleConstants.MAX_RETRY)).thenReturn(1);

        boolean result = service.reportAbandon(new TaskAbandonParam(10L, "127.0.0.1:9000", "停机", null));

        assertTrue(result);
        verify(taskRecordRepo).abandonAndRequeue(10L, TaskScheduleConstants.MAX_RETRY);
        verify(taskRecordRepo, never()).requeueDetached(anyLong());
    }

    /**
     * 验证 detached 上报时任务已不在运行中则返回 false，不回退到普通重试路径。
     */
    @Test
    void testReportAbandonDetachedWhenNotRunningIgnored() {
        AsyncTaskRecord record = runningRecord(10L);
        when(taskRecordRepo.findById(10L)).thenReturn(Optional.of(record));
        when(taskRecordRepo.requeueDetached(10L)).thenReturn(0);

        boolean result = service.reportAbandon(new TaskAbandonParam(10L, "127.0.0.1:9000", "停机挂起", true));

        assertFalse(result);
        verify(taskRecordRepo, never()).abandonAndRequeue(anyLong(), anyInt());
    }

    /**
     * 验证临时任务被挂起放弃时直接置为失败并清理检查点（临时任务不参与故障转移）。
     */
    @Test
    void testReportAbandonTempTaskFailsAndDeletesCheckpoint() {
        AsyncTaskRecord record = runningRecord(10L);
        record.setIsTemp(true);
        when(taskRecordRepo.findById(10L)).thenReturn(Optional.of(record));
        when(taskRecordRepo.failRunningTask(10L)).thenReturn(1);

        boolean result = service.reportAbandon(new TaskAbandonParam(10L, "127.0.0.1:9000", "停机挂起", true));

        assertFalse(result);
        verify(taskRecordRepo, never()).requeueDetached(anyLong());
        verify(taskRecordRepo, never()).abandonAndRequeue(anyLong(), anyInt());
        verify(checkpointService).delete(10L);
    }

    /**
     * 验证任务正常上报 FINISH 后删除检查点。
     */
    @Test
    void testReportFinishDeletesCheckpoint() {
        AsyncTaskRecord record = runningRecord(10L);
        when(taskRecordRepo.findById(10L)).thenReturn(Optional.of(record));

        boolean result = service.reportFinish(new TaskFinishReportParam(10L, "127.0.0.1:9000",
                AsyncTaskConstants.Status.FINISH, "执行完成", null));

        assertTrue(result);
        verify(checkpointService).delete(10L);
        verify(taskRecordRepo).save(record);
    }

    /**
     * 验证执行者失联回收时，有检查点的任务走「不消耗重试次数」的重新排队。
     */
    @Test
    void testReclaimDeadWorkerTasksUsesDetachedForCheckpointedRecords() {
        AsyncTaskRecord checkpointed = runningRecord(1L);
        AsyncTaskRecord normal = runningRecord(2L);
        when(checkpointService.findExistingTaskIds(anyCollection())).thenReturn(Set.of(1L));
        when(taskRecordRepo.requeueDetached(1L)).thenReturn(1);
        when(taskRecordRepo.abandonAndRequeue(2L, TaskScheduleConstants.MAX_RETRY)).thenReturn(1);

        service.reclaimDeadWorkerTasks(List.of(checkpointed, normal), "执行者已失联");

        verify(taskRecordRepo).requeueDetached(1L);
        verify(taskRecordRepo).abandonAndRequeue(2L, TaskScheduleConstants.MAX_RETRY);
        verify(taskRecordRepo, never()).abandonAndRequeue(eq(1L), anyInt());
        verify(checkpointService, never()).delete(any());
        verify(mqService, times(2)).sendBroadcast(eq(TaskMQTopic.TASK_PUBLISH), any());
    }

    /**
     * 验证批量查询检查点失败时回退到普通故障转移回收（消耗重试次数）。
     */
    @Test
    void testReclaimDeadWorkerTasksFallsBackWhenCheckpointQueryFails() {
        AsyncTaskRecord record = runningRecord(1L);
        when(checkpointService.findExistingTaskIds(anyCollection()))
                .thenThrow(new IllegalStateException("模拟数据库异常"));
        when(taskRecordRepo.abandonAndRequeue(1L, TaskScheduleConstants.MAX_RETRY)).thenReturn(1);

        service.reclaimDeadWorkerTasks(List.of(record), "执行者已失联");

        verify(taskRecordRepo).abandonAndRequeue(1L, TaskScheduleConstants.MAX_RETRY);
        verify(taskRecordRepo, never()).requeueDetached(anyLong());
    }

    /**
     * 验证回收临时任务时直接置为失败并清理检查点。
     */
    @Test
    void testReclaimTempTaskFailsAndDeletesCheckpoint() {
        AsyncTaskRecord record = runningRecord(1L);
        record.setIsTemp(true);
        when(checkpointService.findExistingTaskIds(anyCollection())).thenReturn(Set.of(1L));
        when(taskRecordRepo.failRunningTask(1L)).thenReturn(1);

        service.reclaimDeadWorkerTasks(List.of(record), "执行者已失联");

        verify(taskRecordRepo).failRunningTask(1L);
        verify(taskRecordRepo, never()).requeueDetached(anyLong());
        verify(checkpointService).delete(1L);
    }

    /**
     * 验证取消执行者已失联的运行中任务时清理检查点。
     */
    @Test
    void testCancelRunningTaskDeletesCheckpoint() {
        AsyncTaskRecord record = runningRecord(10L);
        when(taskRecordRepo.findById(10L)).thenReturn(Optional.of(record));
        when(taskRecordRepo.updateStatus(10L, AsyncTaskConstants.Status.CANCEL,
                AsyncTaskConstants.Status.RUNNING)).thenReturn(1);

        boolean result = service.cancelRunningTask(10L, "执行者已失联");

        assertTrue(result);
        verify(checkpointService).delete(10L);
    }

    /**
     * 验证过期/不属于该执行者的上报被忽略，不写任何数据。
     */
    @Test
    void testReportFinishFromStaleWorkerIgnored() {
        AsyncTaskRecord record = runningRecord(10L);
        record.setClaimedWorker("other-worker");
        when(taskRecordRepo.findById(10L)).thenReturn(Optional.of(record));

        boolean result = service.reportFinish(new TaskFinishReportParam(10L, "127.0.0.1:9000",
                AsyncTaskConstants.Status.FINISH, "执行完成", null));

        assertFalse(result);
        verify(taskRecordRepo, never()).save(any());
        verify(checkpointService, never()).delete(any());
        verify(logRecordRepo, never()).save(any());
    }

    /**
     * 验证非法的结束状态不会被接受。
     */
    @Test
    void testReportFinishInvalidStatusRejected() {
        boolean result = service.reportFinish(new TaskFinishReportParam(10L, "127.0.0.1:9000",
                AsyncTaskConstants.Status.RUNNING, "非法状态", null));

        assertFalse(result);
        verify(taskRecordRepo, never()).findById(anyLong());
        verify(checkpointService, never()).delete(any());
    }
}
