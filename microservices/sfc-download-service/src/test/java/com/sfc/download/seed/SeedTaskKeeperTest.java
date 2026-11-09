package com.sfc.download.seed;

import com.sfc.download.engine.p2p.Aria2Client;
import com.sfc.download.engine.p2p.Aria2RpcException;
import com.sfc.download.model.po.DownloadSeedTask;
import com.sfc.download.repo.DownloadSeedTaskRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SeedTaskKeeper} 单元测试：到期清理、并发抢占、幂等重试、aria2 不可达降级与异常终态。
 *
 * <p>仓储与 aria2 客户端全部使用 Mockito 替身，本地目录使用 JUnit {@link TempDir}，不启动 Spring 容器。</p>
 */
@ExtendWith(MockitoExtension.class)
class SeedTaskKeeperTest {

    /**
     * 做种记录仓储替身。
     */
    @Mock
    private DownloadSeedTaskRepo seedTaskRepo;

    /**
     * aria2 RPC 客户端替身。
     */
    @Mock
    private Aria2Client aria2Client;

    /**
     * 被测对象。
     */
    @InjectMocks
    private SeedTaskKeeper keeper;

    /**
     * 测试用临时目录（模拟本服务侧任务目录）。
     */
    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("到期记录：抢占成功 → 停止 aria2 → 删除本地目录 → 批量回写已清理终态")
    void scanShouldClaimAndCleanExpiredTask() throws IOException {
        Path localDir = createLocalDir("download-1");
        DownloadSeedTask task = seedTask(1L, 100L, "gid-content", localDir.toString());
        Date now = new Date();
        stubNoStuckRecord();
        when(seedTaskRepo.findExpired(eq(SeedTaskStatus.SEEDING), any(Date.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));
        when(seedTaskRepo.claimExpired(eq(1L), eq(SeedTaskStatus.SEEDING), eq(SeedTaskStatus.CLEANING),
                any(Date.class))).thenReturn(1);
        when(aria2Client.getVersion()).thenReturn("1.37.0");
        when(aria2Client.tellStatus(eq("gid-content"), any())).thenReturn(Map.of("status", "active"));

        int cleaned = keeper.scan(now);

        assertEquals(1, cleaned);
        assertFalse(Files.exists(localDir), "到期后本地目录必须被删除");
        verify(aria2Client).forceRemove("gid-content");
        verify(aria2Client).removeDownloadResult("gid-content");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(seedTaskRepo).updateFinishByIds(idsCaptor.capture(), eq(SeedTaskStatus.CLEANED),
                anyString(), any(Date.class));
        assertTrue(idsCaptor.getValue().contains(1L));
        verify(seedTaskRepo, never()).updateFinish(anyLong(), any(), anyString(), any(Date.class));
    }

    @Test
    @DisplayName("抢占失败（已被其它实例处理）时跳过清理，不重复删除目录")
    void scanShouldSkipWhenClaimFailed() throws IOException {
        Path localDir = createLocalDir("download-2");
        DownloadSeedTask task = seedTask(2L, 200L, "gid-other", localDir.toString());
        stubNoStuckRecord();
        when(seedTaskRepo.findExpired(eq(SeedTaskStatus.SEEDING), any(Date.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));
        when(seedTaskRepo.claimExpired(eq(2L), eq(SeedTaskStatus.SEEDING), eq(SeedTaskStatus.CLEANING),
                any(Date.class))).thenReturn(0);
        when(aria2Client.getVersion()).thenReturn("1.37.0");

        int cleaned = keeper.scan(new Date());

        assertEquals(0, cleaned);
        assertTrue(Files.exists(localDir), "抢占失败时不应删除本地目录");
        verify(aria2Client, never()).tellStatus(anyString(), any());
        verify(seedTaskRepo, never()).updateFinishByIds(any(), any(), anyString(), any(Date.class));
        verify(seedTaskRepo, never()).updateFinish(anyLong(), any(), anyString(), any(Date.class));
    }

    @Test
    @DisplayName("aria2 不可达：只 WARN 并跳过本轮，不查询/抢占记录、不置终态")
    void scanShouldSkipRoundWhenAria2Unreachable() {
        when(aria2Client.getVersion()).thenThrow(new Aria2RpcException(Aria2RpcException.UNKNOWN_CODE,
                "Connection refused"));

        int cleaned = keeper.scan(new Date());

        assertEquals(0, cleaned);
        verifyNoInteractions(seedTaskRepo);
    }

    @Test
    @DisplayName("清理中超过 10 分钟的记录：不加锁直接重试清理（幂等）")
    void scanShouldRetryStuckCleaningTask() throws IOException {
        Path localDir = createLocalDir("download-3");
        DownloadSeedTask task = seedTask(3L, 300L, null, localDir.toString());
        task.setStatus(SeedTaskStatus.CLEANING);
        when(seedTaskRepo.findByStatusAndUpdateAtBefore(eq(SeedTaskStatus.CLEANING), any(Date.class),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(task)));
        when(seedTaskRepo.findExpired(eq(SeedTaskStatus.SEEDING), any(Date.class), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(aria2Client.getVersion()).thenReturn("1.37.0");

        int cleaned = keeper.scan(new Date());

        assertEquals(1, cleaned);
        assertFalse(Files.exists(localDir));
        verify(seedTaskRepo, never()).claimExpired(anyLong(), any(), any(), any(Date.class));
        verify(seedTaskRepo).updateFinishByIds(any(), eq(SeedTaskStatus.CLEANED), anyString(), any(Date.class));
    }

    @Test
    @DisplayName("aria2 移除失败不阻塞本地删除：仍删目录并记录告警信息")
    void scanShouldCleanLocalDirWhenAria2RemoveFails() throws IOException {
        Path localDir = createLocalDir("download-4");
        DownloadSeedTask task = seedTask(4L, 400L, "gid-lost", localDir.toString());
        stubNoStuckRecord();
        when(seedTaskRepo.findExpired(eq(SeedTaskStatus.SEEDING), any(Date.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));
        when(seedTaskRepo.claimExpired(eq(4L), eq(SeedTaskStatus.SEEDING), eq(SeedTaskStatus.CLEANING),
                any(Date.class))).thenReturn(1);
        when(aria2Client.getVersion()).thenReturn("1.37.0");
        when(aria2Client.tellStatus(eq("gid-lost"), any()))
                .thenThrow(new Aria2RpcException(3, "GID gid-lost is not found"));

        int cleaned = keeper.scan(new Date());

        assertEquals(1, cleaned);
        assertFalse(Files.exists(localDir));
        verify(seedTaskRepo).updateFinish(eq(4L), eq(SeedTaskStatus.CLEANED),
                argThat(message -> message != null && message.contains("aria2 清理异常")), any(Date.class));
        verify(seedTaskRepo, never()).updateFinishByIds(any(), any(), anyString(), any(Date.class));
    }

    @Test
    @DisplayName("本地目录删除失败（路径非法）→ 置异常终态并记录原因，不进入已清理")
    void scanShouldMarkErrorWhenLocalDirInvalid() {
        DownloadSeedTask task = seedTask(5L, 500L, null, "bad\u0000dir");
        stubNoStuckRecord();
        when(seedTaskRepo.findExpired(eq(SeedTaskStatus.SEEDING), any(Date.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));
        when(seedTaskRepo.claimExpired(eq(5L), eq(SeedTaskStatus.SEEDING), eq(SeedTaskStatus.CLEANING),
                any(Date.class))).thenReturn(1);
        when(aria2Client.getVersion()).thenReturn("1.37.0");

        int cleaned = keeper.scan(new Date());

        assertEquals(0, cleaned);
        verify(seedTaskRepo).updateFinish(eq(5L), eq(SeedTaskStatus.ERROR),
                argThat(message -> message != null && message.contains("做种清理失败")), any(Date.class));
        verify(seedTaskRepo, never()).updateFinishByIds(any(), any(), anyString(), any(Date.class));
    }

    @Test
    @DisplayName("多条到期记录：成功清理的记录合并为一次批量终态回写")
    void scanShouldBatchUpdateMultipleCleanedTasks() throws IOException {
        Path localDirA = createLocalDir("download-a");
        Path localDirB = createLocalDir("download-b");
        DownloadSeedTask taskA = seedTask(6L, 600L, null, localDirA.toString());
        DownloadSeedTask taskB = seedTask(7L, 700L, null, localDirB.toString());
        stubNoStuckRecord();
        when(seedTaskRepo.findExpired(eq(SeedTaskStatus.SEEDING), any(Date.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(taskA, taskB)));
        when(seedTaskRepo.claimExpired(anyLong(), eq(SeedTaskStatus.SEEDING), eq(SeedTaskStatus.CLEANING),
                any(Date.class))).thenReturn(1);
        when(aria2Client.getVersion()).thenReturn("1.37.0");

        int cleaned = keeper.scan(new Date());

        assertEquals(2, cleaned);
        assertFalse(Files.exists(localDirA));
        assertFalse(Files.exists(localDirB));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(seedTaskRepo).updateFinishByIds(idsCaptor.capture(), eq(SeedTaskStatus.CLEANED),
                anyString(), any(Date.class));
        assertEquals(2, idsCaptor.getValue().size());
    }

    @Test
    @DisplayName("扫描异常（仓储不可用）被收敛：scanQuietly 不抛出")
    void scanQuietlyShouldSwallowRepositoryException() {
        when(aria2Client.getVersion()).thenReturn("1.37.0");
        when(seedTaskRepo.findByStatusAndUpdateAtBefore(any(), any(Date.class), any(Pageable.class)))
                .thenThrow(new IllegalStateException("database is locked"));

        assertDoesNotThrow(() -> keeper.scanQuietly());
    }

    /**
     * 构造做种记录。
     *
     * @param id         记录主键
     * @param downloadId 下载任务主键
     * @param contentGid 内容 gid，允许为 {@code null}
     * @param localDir   本服务侧目录
     * @return 状态为「做种中」的做种记录
     */
    private static DownloadSeedTask seedTask(Long id, Long downloadId, String contentGid, String localDir) {
        DownloadSeedTask task = new DownloadSeedTask();
        task.setId(id);
        task.setDownloadId(downloadId);
        task.setContentGid(contentGid);
        task.setLocalDir(localDir);
        task.setStatus(SeedTaskStatus.SEEDING);
        return task;
    }

    /**
     * 在临时目录下创建一个带文件的目录（模拟做种工作目录）。
     *
     * @param name 目录名
     * @return 已创建的目录
     * @throws IOException 创建失败时抛出
     */
    private Path createLocalDir(String name) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("data.bin"), "content", StandardCharsets.UTF_8);
        return dir;
    }

    /**
     * 打桩：没有需要重试的「清理中」记录。
     */
    private void stubNoStuckRecord() {
        when(seedTaskRepo.findByStatusAndUpdateAtBefore(eq(SeedTaskStatus.CLEANING), any(Date.class),
                any(Pageable.class))).thenReturn(Page.empty());
    }
}
