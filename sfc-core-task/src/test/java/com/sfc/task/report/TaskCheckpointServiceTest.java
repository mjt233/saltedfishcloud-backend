package com.sfc.task.report;

import com.sfc.task.model.AsyncTaskCheckpoint;
import com.sfc.task.repo.AsyncTaskCheckpointRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TaskCheckpointService} 的单元测试：upsert、查询、删除、过期清理与批量查询。
 */
class TaskCheckpointServiceTest {

    private AsyncTaskCheckpointRepo checkpointRepo;

    private TaskCheckpointService service;

    @BeforeEach
    void setUp() {
        checkpointRepo = mock(AsyncTaskCheckpointRepo.class);
        service = new TaskCheckpointService(checkpointRepo);
    }

    /**
     * 验证保存不存在的检查点时新建实体并填充更新时间。
     */
    @Test
    void testSaveCreatesNewCheckpoint() {
        when(checkpointRepo.findByTaskId(1L)).thenReturn(null);

        service.save(1L, "test-type", "{\"phase\":\"DOWNLOADING\"}");

        ArgumentCaptor<AsyncTaskCheckpoint> captor = ArgumentCaptor.forClass(AsyncTaskCheckpoint.class);
        verify(checkpointRepo).save(captor.capture());
        AsyncTaskCheckpoint saved = captor.getValue();
        assertEquals(1L, saved.getTaskId());
        assertEquals("test-type", saved.getTaskType());
        assertEquals("{\"phase\":\"DOWNLOADING\"}", saved.getCheckpoint());
        assertNotNull(saved.getUpdateAt());
    }

    /**
     * 验证保存已存在的检查点时覆盖原内容而不是新增记录。
     */
    @Test
    void testSaveUpdatesExistingCheckpoint() {
        AsyncTaskCheckpoint existing = AsyncTaskCheckpoint.builder()
                .taskId(1L)
                .taskType("old-type")
                .checkpoint("old")
                .updateAt(new Date(0L))
                .build();
        when(checkpointRepo.findByTaskId(1L)).thenReturn(existing);

        service.save(1L, "new-type", "new-checkpoint");

        ArgumentCaptor<AsyncTaskCheckpoint> captor = ArgumentCaptor.forClass(AsyncTaskCheckpoint.class);
        verify(checkpointRepo).save(captor.capture());
        AsyncTaskCheckpoint saved = captor.getValue();
        assertEquals(1L, saved.getTaskId());
        assertEquals("new-type", saved.getTaskType());
        assertEquals("new-checkpoint", saved.getCheckpoint());
        assertTrue(saved.getUpdateAt().getTime() > 0L);
    }

    /**
     * 验证空白检查点视为删除。
     */
    @Test
    void testSaveBlankDeletesCheckpoint() {
        service.save(1L, "test-type", "  ");

        verify(checkpointRepo).deleteByTaskId(1L);
        verify(checkpointRepo, never()).save(any());
    }

    /**
     * 验证超过 1MB 的检查点被拒绝保存，且不抛出异常。
     */
    @Test
    void testSaveOversizedCheckpointRejected() {
        String oversized = "x".repeat(TaskCheckpointService.MAX_CHECKPOINT_LENGTH + 1);

        assertDoesNotThrow(() -> service.save(1L, "test-type", oversized));

        verify(checkpointRepo, never()).save(any());
        verify(checkpointRepo, never()).deleteByTaskId(any());
    }

    /**
     * 验证缺少任务id时忽略保存。
     */
    @Test
    void testSaveNullTaskIdIgnored() {
        service.save(null, "test-type", "cp");

        verifyNoInteractions(checkpointRepo);
    }

    /**
     * 验证查询检查点：存在时返回内容，不存在时返回 null。
     */
    @Test
    void testFind() {
        when(checkpointRepo.findByTaskId(1L)).thenReturn(AsyncTaskCheckpoint.builder()
                .taskId(1L)
                .checkpoint("cp")
                .build());
        when(checkpointRepo.findByTaskId(2L)).thenReturn(null);

        assertEquals("cp", service.find(1L));
        assertNull(service.find(2L));
        assertNull(service.find(null));
    }

    /**
     * 验证删除检查点。
     */
    @Test
    void testDelete() {
        when(checkpointRepo.deleteByTaskId(1L)).thenReturn(1);

        service.delete(1L);

        verify(checkpointRepo).deleteByTaskId(1L);
    }

    /**
     * 验证删除时任务id为空不操作数据库。
     */
    @Test
    void testDeleteNullIgnored() {
        service.delete(null);

        verifyNoInteractions(checkpointRepo);
    }

    /**
     * 验证过期清理按「当前时间 - 最大保留时长」计算阈值。
     */
    @Test
    void testCleanupExpired() {
        when(checkpointRepo.deleteByUpdateAtBefore(any())).thenReturn(3);
        Duration maxAge = Duration.ofDays(30);
        long expectedThreshold = System.currentTimeMillis() - maxAge.toMillis();

        int deleted = service.cleanupExpired(maxAge);

        assertEquals(3, deleted);
        ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);
        verify(checkpointRepo).deleteByUpdateAtBefore(captor.capture());
        assertTrue(Math.abs(captor.getValue().getTime() - expectedThreshold) < 5000L);
    }

    /**
     * 验证非法或空的保留时长不会触发删除。
     */
    @Test
    void testCleanupExpiredWithInvalidMaxAgeSkipped() {
        assertEquals(0, service.cleanupExpired(null));
        assertEquals(0, service.cleanupExpired(Duration.ZERO));
        assertEquals(0, service.cleanupExpired(Duration.ofDays(-1)));

        verifyNoInteractions(checkpointRepo);
    }

    /**
     * 验证批量查询检查点时按每批 500 个拆分，避免单条 SQL 过长。
     */
    @Test
    void testFindExistingTaskIdsBatched() {
        when(checkpointRepo.findExistingTaskIds(anyCollection()))
                .thenReturn(List.of(1L))
                .thenReturn(List.of(2L));
        List<Long> taskIds = LongStream.rangeClosed(1, 600).boxed().toList();

        Set<Long> result = service.findExistingTaskIds(taskIds);

        assertEquals(Set.of(1L, 2L), result);
        verify(checkpointRepo, times(2)).findExistingTaskIds(anyCollection());
    }

    /**
     * 验证空集合查询不访问数据库。
     */
    @Test
    void testFindExistingTaskIdsEmptySkipped() {
        assertEquals(Set.of(), service.findExistingTaskIds(List.of()));
        assertEquals(Set.of(), service.findExistingTaskIds(null));

        verifyNoInteractions(checkpointRepo);
    }
}
