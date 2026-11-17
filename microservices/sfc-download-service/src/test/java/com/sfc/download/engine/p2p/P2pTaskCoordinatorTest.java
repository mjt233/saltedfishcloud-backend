package com.sfc.download.engine.p2p;

import com.sfc.cloud.task.worker.SfcTaskDetachedException;
import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.cloud.task.worker.SfcTaskProgress;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.config.SfcDownloadInternalProperties;
import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.model.DownloadTaskParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

/**
 * {@link P2pTaskCoordinator} 单测：resume 三分支、检查点落盘、上传幂等、收口矩阵与磁力 gid 跟随同步。
 */
@ExtendWith(MockitoExtension.class)
class P2pTaskCoordinatorTest {

    /**
     * aria2 下载引擎（Mock）。
     */
    @Mock
    private Aria2DownloadEngine aria2Engine;

    /**
     * aria2 共享工作区（Mock）。
     */
    @Mock
    private Aria2Workspace aria2Workspace;

    /**
     * 做种 SPI 提供者。
     */
    @Mock
    private ObjectProvider<P2pSeedRegistrar> seedRegistrarProvider;

    /**
     * 做种 SPI 实现。
     */
    @Mock
    private P2pSeedRegistrar seedRegistrar;

    /**
     * Worker 任务执行上下文（Mock）。
     */
    @Mock
    private SfcTaskExecution execution;

    /**
     * 测试用临时根目录。
     */
    @TempDir
    Path tempRoot;

    /**
     * 内部调优配置（使用较短的等待间隔以加快测试）。
     */
    private SfcDownloadInternalProperties internalProperties;

    /**
     * 被测协调器。
     */
    private P2pTaskCoordinator coordinator;

    /**
     * 当前检查点（模拟 SDK 中最近一次保存的值）。
     */
    private final AtomicReference<String> checkpoint = new AtomicReference<>();

    /**
     * 历次保存的检查点 JSON（用于断言落盘内容）。
     */
    private final List<String> savedCheckpoints = new ArrayList<>();

    /**
     * 下载任务参数。
     */
    private DownloadTaskParams params;

    /**
     * 初始化协调器、执行上下文桩与通用桩。
     */
    @BeforeEach
    void setUp() {
        internalProperties = new SfcDownloadInternalProperties();
        internalProperties.getResume().setUploadWaitIntervalMillis(50L);
        internalProperties.getResume().setUploadWaitAttempts(2);
        lenient().when(seedRegistrarProvider.getIfAvailable(anySupplier())).thenReturn(seedRegistrar);
        coordinator = new P2pTaskCoordinator(aria2Engine, aria2Workspace, seedRegistrarProvider, internalProperties);

        lenient().when(execution.getProgress()).thenReturn(new SfcTaskProgress());
        lenient().when(execution.isInterruptRequested()).thenReturn(false);
        lenient().when(execution.isDetachRequested()).thenReturn(false);
        lenient().when(execution.saveCheckpoint(anyString())).thenAnswer(invocation -> {
            String json = invocation.getArgument(0);
            savedCheckpoints.add(json);
            checkpoint.set(json);
            return true;
        });
        lenient().when(execution.getCheckpoint()).thenAnswer(invocation -> checkpoint.get());

        params = new DownloadTaskParams();
        params.downloadId = 100L;
        params.type = DownloadType.MAGNET;
        params.uid = 1L;
        params.savePath = "/save";
    }

    /**
     * 新建任务：提交 → 轮询 → 采集 → DOWNLOADED，检查点落盘阶段正确。
     */
    @Test
    void shouldStartNewDownloadAndSaveCheckpoints() {
        stubWorkspace();
        Aria2TaskSession session = new Aria2TaskSession("g1");
        when(aria2Engine.submitSession(any())).thenReturn(session);
        when(aria2Engine.awaitCompletion(any(), any(), any())).thenReturn(Map.of("status", "complete"));
        when(aria2Engine.collectArtifacts(any(), any())).thenReturn(List.of());
        when(seedRegistrar.register(any(), any(), any())).thenReturn(false);

        P2pTaskCoordinator.P2pTaskResult result = coordinator.start(newContext());

        assertEquals(P2pTaskState.Phase.DOWNLOADED, result.state().getPhase());
        assertEquals(List.of("g1"), result.state().getGids());
        assertFalse(result.state().isSeeding());
        assertNotNull(result.state().getLocalDir());
        assertEquals(P2pTaskState.Phase.DOWNLOADED, lastCheckpoint().getPhase());
        verify(execution, atLeastOnce()).saveCheckpoint(anyString());
        verify(aria2Engine, never()).discardTaskResults(any());
    }

    /**
     * 新建任务且做种登记成功：seeding / seedTaskId / seedEndAt 写入检查点。
     */
    @Test
    void shouldPersistSeedingFieldsAfterRegistration() {
        stubWorkspace();
        Aria2TaskSession session = new Aria2TaskSession("g1");
        when(aria2Engine.submitSession(any())).thenReturn(session);
        when(aria2Engine.awaitCompletion(any(), any(), any())).thenReturn(Map.of("status", "complete"));
        when(aria2Engine.collectArtifacts(any(), any())).thenReturn(List.of());
        when(seedRegistrar.register(any(), any(), any())).thenAnswer(invocation -> {
            P2pTaskState state = invocation.getArgument(1);
            state.setSeeding(true);
            state.setSeedTaskId(998877L);
            state.setSeedEndAt(1750604800000L);
            return true;
        });

        P2pTaskCoordinator.P2pTaskResult result = coordinator.start(newContext());

        assertTrue(result.state().isSeeding());
        P2pTaskState persisted = lastCheckpoint();
        assertTrue(persisted.isSeeding());
        assertEquals(998877L, persisted.getSeedTaskId());
        assertEquals(1750604800000L, persisted.getSeedEndAt());
    }

    /**
     * 命中可复用做种文件时不再提交 aria2，直接采集既有目录产物。
     *
     * @throws IOException 创建文件失败
     */
    @Test
    void shouldReuseSeedingFilesWithoutSubmit() throws IOException {
        Path seedDir = Files.createDirectory(tempRoot.resolve("seed-dir"));
        Files.writeString(seedDir.resolve("movie.mp4"), "data");
        when(seedRegistrar.findReusable(any())).thenReturn(Optional.of(new P2pSeedReuseTarget(
                9L, 8L, "content-gid", "/downloads/seed", seedDir.toString(),
                new Date(System.currentTimeMillis() + 60_000L))));

        P2pTaskCoordinator.P2pTaskResult result = coordinator.start(newContext());

        assertEquals(1, result.artifacts().size());
        assertEquals("movie.mp4", result.artifacts().getFirst().relativePath());
        assertTrue(result.state().isSeeding());
        assertEquals(9L, result.state().getSeedTaskId());
        assertEquals(P2pTaskState.Phase.DOWNLOADED, result.state().getPhase());
        verify(aria2Engine, never()).submitSession(any());
    }

    /**
     * 复用目标的目录不存在时降级为正常下载。
     */
    @Test
    void shouldFallBackToNormalDownloadWhenReuseTargetMissing() {
        when(seedRegistrar.findReusable(any())).thenReturn(Optional.of(new P2pSeedReuseTarget(
                9L, 8L, "content-gid", "/downloads/seed",
                tempRoot.resolve("not-exists").toString(), new Date())));
        stubWorkspace();
        Aria2TaskSession session = new Aria2TaskSession("g1");
        when(aria2Engine.submitSession(any())).thenReturn(session);
        when(aria2Engine.awaitCompletion(any(), any(), any())).thenReturn(Map.of("status", "complete"));
        when(aria2Engine.collectArtifacts(any(), any())).thenReturn(List.of());
        when(seedRegistrar.register(any(), any(), any())).thenReturn(false);

        coordinator.start(newContext());

        verify(aria2Engine).submitSession(any());
    }

    /**
     * 回归 P5-D01：aria2 下载完成后仍处于做种（active + seeder=true）时，引擎返回的完成状态应让协调器
     * 正常把阶段推进到 DOWNLOADED 并登记做种，而不是停在 DOWNLOADING 等超时。
     */
    @Test
    void shouldRegisterSeedingWhenAria2IsSeeding() {
        stubWorkspace();
        Aria2TaskSession session = new Aria2TaskSession("g1");
        when(aria2Engine.submitSession(any())).thenReturn(session);
        when(aria2Engine.awaitCompletion(any(), any(), any())).thenReturn(Map.of(
                "status", "active", "seeder", "true",
                "completedLength", "2048", "totalLength", "2048"));
        when(aria2Engine.collectArtifacts(any(), any())).thenReturn(List.of());
        when(seedRegistrar.register(any(), any(), any())).thenAnswer(invocation -> {
            P2pTaskState state = invocation.getArgument(1);
            state.setSeeding(true);
            state.setSeedTaskId(66L);
            state.setSeedEndAt(System.currentTimeMillis() + 60_000L);
            return true;
        });

        P2pTaskCoordinator.P2pTaskResult result = coordinator.start(newContext());

        assertEquals(P2pTaskState.Phase.DOWNLOADED, result.state().getPhase());
        assertTrue(result.state().isSeeding(), "做种中的 aria2 任务也必须完成做种登记");
        assertEquals(66L, lastCheckpoint().getSeedTaskId());
    }

    /**
     * URL 形式种子（P5-D02）：复用查询之前必须先把种子内容拉取并回填到任务参数，否则
     * {@code torrent_sha256} 复用键恒为空、同种子复用永远不生效。
     *
     * @throws IOException 创建做种目录失败
     */
    @Test
    void shouldEnsureTorrentContentBeforeReuseLookup() throws IOException {
        params.type = DownloadType.TORRENT;
        params.url = "https://example.com/a.torrent";
        params.torrentBase64 = null;
        Path seedDir = Files.createDirectory(tempRoot.resolve("torrent-seed-dir"));
        Files.writeString(seedDir.resolve("movie.mp4"), "data");
        doAnswer(invocation -> {
            DownloadContext context = invocation.getArgument(0);
            context.getParams().torrentBase64 = "c29tZS10b3JyZW50";
            return null;
        }).when(aria2Engine).ensureTorrentBase64(any());
        when(seedRegistrar.findReusable(any())).thenAnswer(invocation -> {
            assertTrue(SfcStringUtils.hasText(params.torrentBase64), "复用查询前必须已回填种子内容");
            return Optional.of(new P2pSeedReuseTarget(9L, 8L, "content-gid", "/downloads/seed",
                    seedDir.toString(), new Date(System.currentTimeMillis() + 60_000L)));
        });

        P2pTaskCoordinator.P2pTaskResult result = coordinator.start(newContext());

        assertEquals(1, result.artifacts().size());
        assertTrue(result.state().isSeeding());
        verify(aria2Engine, never()).submitSession(any());
        InOrder inOrder = inOrder(aria2Engine, seedRegistrar);
        inOrder.verify(aria2Engine).ensureTorrentBase64(any());
        inOrder.verify(seedRegistrar).findReusable(any());
    }

    /**
     * 接管恢复（DOWNLOADED 阶段）时同样回填种子内容，保证补登记做种能算出 {@code torrent_sha256}。
     *
     * @throws IOException 创建任务目录失败
     */
    @Test
    void shouldEnsureTorrentContentOnResume() throws IOException {
        params.type = DownloadType.TORRENT;
        params.url = "https://example.com/a.torrent";
        params.torrentBase64 = null;
        Path taskDir = createTaskDir("100");
        Files.writeString(taskDir.resolve("data.bin"), "x");
        P2pTaskState state = state(P2pTaskState.Phase.DOWNLOADED, taskDir, List.of("g1"));
        doAnswer(invocation -> {
            DownloadContext context = invocation.getArgument(0);
            context.getParams().torrentBase64 = "c29tZS10b3JyZW50";
            return null;
        }).when(aria2Engine).ensureTorrentBase64(any());
        when(seedRegistrar.register(any(), any(), any())).thenAnswer(invocation -> {
            assertTrue(SfcStringUtils.hasText(params.torrentBase64), "登记做种前必须已回填种子内容");
            return false;
        });

        coordinator.resume(state, newContext());

        verify(aria2Engine).ensureTorrentBase64(any());
        verify(seedRegistrar).register(any(), any(), any());
    }

    /**
     * resume 分支一：活动 gid 存在 → 重建会话继续轮询，接管计数 +1。
     */
    @Test
    void shouldResumeWithAliveGid() {
        Path taskDir = createTaskDir("100");
        P2pTaskState state = state(P2pTaskState.Phase.DOWNLOADING, taskDir, List.of("g1"));
        Aria2TaskSession session = new Aria2TaskSession("g1");
        session.trackContentGid("g1");
        when(aria2Engine.filterAliveGids(List.of("g1"))).thenReturn(List.of("g1"));
        when(aria2Engine.resumeSession(any(), eq(List.of("g1")))).thenReturn(session);
        when(aria2Engine.awaitCompletion(any(), any(), any())).thenReturn(Map.of("status", "complete"));
        when(aria2Engine.collectArtifacts(any(), any())).thenReturn(List.of());
        when(seedRegistrar.register(any(), any(), any())).thenReturn(false);

        P2pTaskCoordinator.P2pTaskResult result = coordinator.resume(state, newContext());

        assertEquals(1, result.state().getResumeCount());
        assertEquals(P2pTaskState.Phase.DOWNLOADED, result.state().getPhase());
        verify(aria2Engine, never()).submitSession(any());
        assertTrue(savedCheckpoints.stream().anyMatch(json -> json.contains("\"resumeCount\":\"1\"")
                || json.contains("\"resumeCount\":1")));
    }

    /**
     * resume 分支二：gid 全部丢失 → 在同一目录以 continue=true 重新提交（由引擎保证）。
     */
    @Test
    void shouldResubmitInSameDirectoryWhenGidLost() {
        Path taskDir = createTaskDir("100");
        P2pTaskState state = state(P2pTaskState.Phase.DOWNLOADING, taskDir, List.of("g1"));
        Aria2TaskSession session = new Aria2TaskSession("g2");
        when(aria2Engine.filterAliveGids(List.of("g1"))).thenReturn(List.of());
        when(aria2Engine.submitSession(any())).thenReturn(session);
        when(aria2Engine.awaitCompletion(any(), any(), any())).thenReturn(Map.of("status", "complete"));
        when(aria2Engine.collectArtifacts(any(), any())).thenReturn(List.of());
        when(seedRegistrar.register(any(), any(), any())).thenReturn(false);

        P2pTaskCoordinator.P2pTaskResult result = coordinator.resume(state, newContext());

        assertEquals(List.of("g2"), result.state().getGids());
        assertEquals(P2pTaskState.Phase.DOWNLOADED, result.state().getPhase());
        verify(aria2Engine).submitSession(any());
        // 同一目录：会话上下文采用的目录必须等于检查点记录的目录
        assertEquals(taskDir.toAbsolutePath().normalize(), Path.of(result.state().getLocalDir()));
    }

    /**
     * resume 分支三：下载已完成（DOWNLOADED）→ 直接采集既有产物，不访问 aria2。
     *
     * @throws IOException 创建文件失败
     */
    @Test
    void shouldCollectExistingArtifactsForDownloadedPhase() throws IOException {
        Path taskDir = createTaskDir("100");
        Files.writeString(taskDir.resolve("data.bin"), "1234567890");
        P2pTaskState state = state(P2pTaskState.Phase.DOWNLOADED, taskDir, List.of("g1"));
        when(seedRegistrar.register(any(), any(), any())).thenReturn(false);

        P2pTaskCoordinator.P2pTaskResult result = coordinator.resume(state, newContext());

        assertEquals(1, result.artifacts().size());
        assertEquals(10L, result.artifacts().getFirst().size());
        assertEquals(P2pTaskState.Phase.DOWNLOADED, result.state().getPhase());
        assertFalse(result.state().isSeeding());
        verifyNoInteractions(aria2Engine);
    }

    /**
     * 接管次数超过上限：主动失败，不再恢复。
     */
    @Test
    void shouldFailWhenResumeCountExceeded() {
        Path taskDir = createTaskDir("100");
        P2pTaskState state = state(P2pTaskState.Phase.DOWNLOADING, taskDir, List.of("g1"));
        state.setResumeCount(50);

        assertThrows(SfcJsonException.class, () -> coordinator.resume(state, newContext()));

        assertEquals(51, lastCheckpoint().getResumeCount());
        verify(aria2Engine, never()).submitSession(any());
        verify(aria2Engine, never()).resumeSession(any(), any());
    }

    /**
     * DOWNLOADED 阶段目录丢失：降级为重新下载。
     */
    @Test
    void shouldRedownloadWhenDownloadedDirectoryMissing() {
        Path missingDir = tempRoot.resolve("100-missing");
        P2pTaskState state = state(P2pTaskState.Phase.DOWNLOADED, missingDir, List.of("g1"));
        stubWorkspace();
        Aria2TaskSession session = new Aria2TaskSession("g9");
        when(aria2Engine.submitSession(any())).thenReturn(session);
        when(aria2Engine.awaitCompletion(any(), any(), any())).thenReturn(Map.of("status", "complete"));
        when(aria2Engine.collectArtifacts(any(), any())).thenReturn(List.of());
        when(seedRegistrar.register(any(), any(), any())).thenReturn(false);

        P2pTaskCoordinator.P2pTaskResult result = coordinator.resume(state, newContext());

        assertEquals(List.of("g9"), result.state().getGids());
        verify(aria2Engine).submitSession(any());
    }

    /**
     * 上传前后检查点字段：UPLOADING + uploadStartedAt；UPLOADED + uploaded/savedName/savedBytes。
     */
    @Test
    void shouldPersistUploadIdempotencyFields() {
        DownloadContext context = newContext();
        P2pTaskState state = state(P2pTaskState.Phase.DOWNLOADED, tempRoot.resolve("100"), List.of("g1"));

        coordinator.markUploading(context, state);
        P2pTaskState uploading = lastCheckpoint();
        assertEquals(P2pTaskState.Phase.UPLOADING, uploading.getPhase());
        assertNotNull(uploading.getUploadStartedAt());
        assertFalse(uploading.isUploaded());

        coordinator.markUploaded(context, state, "saved.bin", 2048L);
        P2pTaskState uploaded = lastCheckpoint();
        assertEquals(P2pTaskState.Phase.UPLOADED, uploaded.getPhase());
        assertTrue(uploaded.isUploaded());
        assertEquals("saved.bin", uploaded.getSavedName());
        assertEquals(2048L, uploaded.getSavedBytes());
    }

    /**
     * 磁力链 gid 跟随：轮询回调把跟随出的内容 gid 同步进检查点（供停机接管时重连）。
     */
    @Test
    void shouldSaveFollowedGidsWhenSessionChangesDuringPolling() {
        stubWorkspace();
        Aria2TaskSession session = new Aria2TaskSession("meta");
        when(aria2Engine.submitSession(any())).thenReturn(session);
        when(aria2Engine.awaitCompletion(any(), any(), any())).thenAnswer(invocation -> {
            Consumer<Aria2TaskSession> listener = invocation.getArgument(2);
            session.track("content");
            session.trackContentGid("content");
            listener.accept(session);
            return Map.of("status", "complete");
        });
        when(aria2Engine.collectArtifacts(any(), any())).thenReturn(List.of());
        when(seedRegistrar.register(any(), any(), any())).thenReturn(false);

        coordinator.start(newContext());

        P2pTaskState persisted = lastCheckpoint();
        assertEquals(List.of("meta", "content"), persisted.getGids());
        assertEquals(List.of("content"), persisted.getContentGids());
    }

    /**
     * 上传幂等：陈旧的上传检查点立即按陈旧处理（不再等待）。
     */
    @Test
    void shouldTreatStaleUploadCheckpointAsStale() {
        P2pTaskState state = state(P2pTaskState.Phase.UPLOADING, tempRoot.resolve("100"), List.of("g1"));
        state.setUploadStartedAt(System.currentTimeMillis() - 11 * 60_000L);
        long start = System.currentTimeMillis();

        Optional<P2pTaskState> settled = coordinator.awaitUploadSettled(newContext(), state);

        assertTrue(settled.isPresent());
        assertFalse(settled.get().isUploaded());
        assertTrue(System.currentTimeMillis() - start < 2_000L, "陈旧检查点不应长时间等待");
    }

    /**
     * 上传幂等：新鲜的上传检查点在等待期间变为 UPLOADED → 返回最新检查点（不再重复上传）。
     */
    @Test
    void shouldReturnUploadedCheckpointWhenObservedDuringWait() {
        P2pTaskState uploadingState = state(P2pTaskState.Phase.UPLOADING, tempRoot.resolve("100"), List.of("g1"));
        uploadingState.setUploadStartedAt(System.currentTimeMillis());
        P2pTaskState uploadedState = state(P2pTaskState.Phase.UPLOADED, tempRoot.resolve("100"), List.of("g1"));
        uploadedState.setUploaded(true);
        uploadedState.setSavedName("x.bin");
        when(execution.getCheckpoint()).thenReturn(uploadedState.toJson());

        Optional<P2pTaskState> settled = coordinator.awaitUploadSettled(newContext(), uploadingState);

        assertTrue(settled.isPresent());
        assertTrue(settled.get().isUploaded());
    }

    /**
     * 上传幂等：新鲜但等待结束仍未变为 UPLOADED → 返回原检查点（调用方按陈旧重新上传）。
     */
    @Test
    void shouldFallBackWhenUploadNeverSettles() {
        P2pTaskState state = state(P2pTaskState.Phase.UPLOADING, tempRoot.resolve("100"), List.of("g1"));
        state.setUploadStartedAt(System.currentTimeMillis());

        Optional<P2pTaskState> settled = coordinator.awaitUploadSettled(newContext(), state);

        assertTrue(settled.isPresent());
        assertFalse(settled.get().isUploaded());
        assertSame(state, settled.get());
    }

    /**
     * 收口矩阵：成功 + 无做种 → 清理 aria2 结果与本地目录。
     *
     * @throws IOException 创建文件失败
     */
    @Test
    void shouldCleanupOnSuccessWithoutSeeding() throws IOException {
        Path taskDir = createTaskDir("100");
        P2pTaskState state = state(P2pTaskState.Phase.UPLOADED, taskDir, List.of("g1"));
        state.setSeeding(false);

        coordinator.finishSuccess(newContext(), state);

        verify(aria2Engine).discardTaskResults(argThat(gids -> gids.contains("g1")));
        assertFalse(Files.exists(taskDir));
    }

    /**
     * 收口矩阵：成功 + 已登记做种 → 保留 aria2 与本地目录。
     *
     * @throws IOException 创建文件失败
     */
    @Test
    void shouldKeepDirectoryOnSuccessWithSeeding() throws IOException {
        Path taskDir = createTaskDir("100");
        P2pTaskState state = state(P2pTaskState.Phase.UPLOADED, taskDir, List.of("g1"));
        state.setSeeding(true);

        coordinator.finishSuccess(newContext(), state);

        verify(aria2Engine, never()).discardTaskResults(any());
        assertTrue(Files.isDirectory(taskDir));
    }

    /**
     * 收口矩阵：上传失败 + 做种已登记 → 保留做种现场。
     *
     * @throws IOException 创建文件失败
     */
    @Test
    void shouldKeepSeedingOnFailure() throws IOException {
        Path taskDir = createTaskDir("100");
        P2pTaskState state = state(P2pTaskState.Phase.UPLOADING, taskDir, List.of("g1"));
        state.setSeeding(true);

        coordinator.releaseOnFailure(newContext(), state);

        verify(aria2Engine, never()).discardTaskResults(any());
        assertTrue(Files.isDirectory(taskDir));
    }

    /**
     * 收口矩阵：上传失败 + 无做种 → 清理 aria2 与本地目录。
     *
     * @throws IOException 创建文件失败
     */
    @Test
    void shouldCleanupOnFailureWithoutSeeding() throws IOException {
        Path taskDir = createTaskDir("100");
        P2pTaskState state = state(P2pTaskState.Phase.UPLOADING, taskDir, List.of("g1"));

        coordinator.releaseOnFailure(newContext(), state);

        verify(aria2Engine).discardTaskResults(argThat(gids -> gids.contains("g1")));
        assertFalse(Files.exists(taskDir));
    }

    /**
     * 收口矩阵：用户取消 → forceRemove + removeDownloadResult + 删除目录。
     *
     * @throws IOException 创建文件失败
     */
    @Test
    void shouldCleanupOnCancel() throws IOException {
        Path taskDir = createTaskDir("100");
        P2pTaskState state = state(P2pTaskState.Phase.DOWNLOADING, taskDir, List.of("g1"));

        coordinator.releaseOnCancel(newContext(), state);

        verify(aria2Engine).discardTaskResults(argThat(gids -> gids.contains("g1")));
        assertFalse(Files.exists(taskDir));
    }

    /**
     * 收口矩阵：取消但目录属于做种保留（复用场景）→ 保持不动。
     *
     * @throws IOException 创建文件失败
     */
    @Test
    void shouldNotCleanupSeedingDirectoryOnCancel() throws IOException {
        Path taskDir = createTaskDir("100");
        P2pTaskState state = state(P2pTaskState.Phase.UPLOADING, taskDir, List.of("g1"));
        state.setSeeding(true);

        coordinator.releaseOnCancel(newContext(), state);

        verify(aria2Engine, never()).discardTaskResults(any());
        assertTrue(Files.isDirectory(taskDir));
    }

    /**
     * 挂起：保存检查点（阶段保持下载中、gid 同步）后向上抛出，且不做收口清理。
     */
    @Test
    void shouldSaveCheckpointAndRethrowWhenDetached() {
        stubWorkspace();
        Aria2TaskSession session = new Aria2TaskSession("g1");
        when(aria2Engine.submitSession(any())).thenReturn(session);
        when(aria2Engine.awaitCompletion(any(), any(), any()))
                .thenThrow(new SfcTaskDetachedException("执行者停机"));

        assertThrows(SfcTaskDetachedException.class, () -> coordinator.start(newContext()));

        P2pTaskState saved = lastCheckpoint();
        assertEquals(P2pTaskState.Phase.DOWNLOADING, saved.getPhase());
        assertEquals(List.of("g1"), saved.getGids());
        verify(aria2Engine, never()).discardTaskResults(any());
    }

    /**
     * 采集既有产物：UPLOADING 阶段重新上传前使用，且上下文复用检查点目录。
     *
     * @throws IOException 创建文件失败
     */
    @Test
    void shouldCollectExistingArtifactsForUploadingPhase() throws IOException {
        Path taskDir = createTaskDir("100");
        Files.writeString(taskDir.resolve("data.bin"), "abc");
        P2pTaskState state = state(P2pTaskState.Phase.UPLOADING, taskDir, List.of("g1"));
        DownloadContext context = newContext();

        P2pTaskCoordinator.P2pTaskResult result = coordinator.collectExisting(state, context);

        assertEquals(1, result.artifacts().size());
        assertEquals(taskDir.toAbsolutePath().normalize(), context.getTempDir());
    }

    /**
     * 采集既有产物：目录中没有文件时抛业务异常。
     */
    @Test
    void shouldFailCollectingEmptyDirectory() {
        Path taskDir = createTaskDir("100");
        P2pTaskState state = state(P2pTaskState.Phase.UPLOADING, taskDir, List.of("g1"));

        assertThrows(SfcJsonException.class, () -> coordinator.collectExisting(state, newContext()));
    }

    /**
     * 检查点缺失、非法、版本不支持或下载任务不匹配时，loadResumable 返回 null（按新任务处理）。
     */
    @Test
    void shouldReturnNullForInvalidCheckpoint() {
        DownloadContext context = newContext();
        when(execution.getCheckpoint()).thenReturn(null);
        assertNull(coordinator.loadResumable(context));
        when(execution.getCheckpoint()).thenReturn("{bad");
        assertNull(coordinator.loadResumable(context));

        P2pTaskState otherTask = new P2pTaskState();
        otherTask.setDownloadId(999L);
        otherTask.setPhase(P2pTaskState.Phase.DOWNLOADING);
        when(execution.getCheckpoint()).thenReturn(otherTask.toJson());
        assertNull(coordinator.loadResumable(context), "检查点与当前任务不匹配时应按无检查点处理");
    }

    /**
     * 合法检查点：loadResumable 正常返回并保持字段。
     */
    @Test
    void shouldLoadValidCheckpoint() {
        P2pTaskState state = state(P2pTaskState.Phase.DOWNLOADING, tempRoot.resolve("100"), List.of("g1"));
        when(execution.getCheckpoint()).thenReturn(state.toJson());

        P2pTaskState loaded = coordinator.loadResumable(newContext());

        assertNotNull(loaded);
        assertEquals(P2pTaskState.Phase.DOWNLOADING, loaded.getPhase());
        assertEquals(List.of("g1"), loaded.getGids());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造执行上下文（任务目录创建到 tempRoot 下）。
     *
     * @return 执行上下文
     */
    private DownloadContext newContext() {
        return new DownloadContext(execution, params, null, taskFlag -> {
            try {
                return Files.createDirectories(tempRoot.resolve("task-" + taskFlag));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * 创建已存在的任务目录。
     *
     * @param name 目录名
     * @return 目录路径
     */
    private Path createTaskDir(String name) {
        try {
            return Files.createDirectories(tempRoot.resolve(name));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 构造检查点。
     *
     * @param phase    阶段
     * @param localDir 本服务任务目录
     * @param gids     gid 列表
     * @return 检查点
     */
    private P2pTaskState state(P2pTaskState.Phase phase, Path localDir, List<String> gids) {
        P2pTaskState state = new P2pTaskState();
        state.setDownloadId(100L);
        state.setPhase(phase);
        state.setLocalDir(localDir.toAbsolutePath().normalize().toString());
        state.setAria2Dir("/downloads/" + localDir.getFileName());
        state.replaceGids(gids);
        state.setSubmittedAt(System.currentTimeMillis());
        state.touch();
        return state;
    }

    /**
     * 返回最近一次保存的检查点。
     *
     * @return 已解析的检查点
     */
    private P2pTaskState lastCheckpoint() {
        assertFalse(savedCheckpoints.isEmpty(), "应至少保存过一次检查点");
        Optional<P2pTaskState> parsed = P2pTaskState.fromJson(savedCheckpoints.getLast());
        assertTrue(parsed.isPresent(), "保存的检查点应可解析");
        return parsed.get();
    }

    /**
     * 桩定共享工作区的 aria2 侧目录换算。
     */
    private void stubWorkspace() {
        lenient().when(aria2Workspace.toAria2Dir(any())).thenReturn("/downloads/offline-download/task-100");
    }

    /**
     * 匹配 {@link ObjectProvider#getIfAvailable(Supplier)} 的任意参数。
     *
     * @param <T> 提供者类型
     * @return 参数匹配器
     */
    @SuppressWarnings("unchecked")
    private static <T> Supplier<T> anySupplier() {
        return (Supplier<T>) any(Supplier.class);
    }
}
