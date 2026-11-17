package com.sfc.download.task;

import com.sfc.cloud.model.SfcFileInfo;
import com.sfc.cloud.remote.client.SfcCoreFileClient;
import com.sfc.cloud.task.worker.SfcTaskDetachedException;
import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.cloud.task.worker.SfcTaskProgress;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadEngineRegistry;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.engine.p2p.Aria2Workspace;
import com.sfc.download.engine.p2p.P2pTaskCoordinator;
import com.sfc.download.engine.p2p.P2pTaskCoordinator.P2pTaskResult;
import com.sfc.download.engine.p2p.P2pTaskState;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.repo.DownloadTaskRepo;
import com.sfc.download.stash.DownloadSaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DownloadTaskHandler} 的 P2P 接管分支测试：UPLOADED 补报、UPLOADING 幂等重传、上传后挂起、失败/取消收口。
 *
 * <p>协调器与落盘服务均使用 Mockito 桩定，不启动 Spring、不访问数据库与文件网盘。</p>
 */
@ExtendWith(MockitoExtension.class)
class DownloadTaskHandlerTest {

    /**
     * 下载任务仓储。
     */
    @Mock
    private DownloadTaskRepo downloadTaskRepo;

    /**
     * core 网盘文件客户端（目录校验）。
     */
    @Mock
    private SfcCoreFileClient fileClient;

    /**
     * 下载引擎注册表。
     */
    @Mock
    private DownloadEngineRegistry engineRegistry;

    /**
     * 落盘服务。
     */
    @Mock
    private DownloadSaveService downloadSaveService;

    /**
     * aria2 共享工作区。
     */
    @Mock
    private Aria2Workspace aria2Workspace;

    /**
     * P2P 协调器。
     */
    @Mock
    private P2pTaskCoordinator p2pTaskCoordinator;

    /**
     * Worker 任务执行上下文。
     */
    @Mock
    private SfcTaskExecution execution;

    /**
     * 测试用临时目录（存放产物文件）。
     */
    @TempDir
    Path tempRoot;

    /**
     * 被测处理器。
     */
    private DownloadTaskHandler handler;

    /**
     * 下载任务参数。
     */
    private DownloadTaskParams params;

    /**
     * 初始化处理器与通用桩。
     */
    @BeforeEach
    void setUp() {
        handler = new DownloadTaskHandler(downloadTaskRepo, fileClient, engineRegistry,
                downloadSaveService, aria2Workspace, p2pTaskCoordinator);

        params = new DownloadTaskParams();
        params.downloadId = 100L;
        params.type = DownloadType.MAGNET;
        params.uid = 1L;
        params.savePath = "/save";

        lenient().when(execution.getTaskId()).thenReturn(1L);
        lenient().when(execution.getParams()).thenReturn(SfcMapperHolder.toJsonNoEx(params));
        lenient().when(execution.getProgress()).thenReturn(new SfcTaskProgress());
        lenient().when(execution.isInterruptRequested()).thenReturn(false);
        lenient().when(execution.isDetachRequested()).thenReturn(false);
        lenient().when(execution.saveCheckpoint(anyString())).thenReturn(true);
        lenient().when(fileClient.list(anyLong(), anyString()))
                .thenReturn(SfcJsonResult.getInstance(List.<SfcFileInfo>of()));
    }

    /**
     * 处理器声明支持挂起（P2P 现场可保留）。
     */
    @Test
    void shouldSupportDetach() {
        assertTrue(handler.supportsDetach());
    }

    /**
     * 检查点 phase=UPLOADED：直接补报 FINISH，不重复上传、不再进入上传阶段。
     */
    @Test
    void shouldReportFinishDirectlyWhenCheckpointUploaded() {
        P2pTaskState state = uploadedState();
        when(p2pTaskCoordinator.loadResumable(any())).thenReturn(state);

        handler.execute(execution);

        verify(downloadTaskRepo).updateStatus(100L, DownloadTaskConstants.Status.RUNNING);
        verify(downloadTaskRepo).updateFinish(eq(100L), eq(DownloadTaskConstants.Status.FINISH),
                eq(1024L), eq(1024L), eq("x.bin"), any(Date.class));
        verify(p2pTaskCoordinator).finishSuccess(any(), eq(state));
        verify(p2pTaskCoordinator, never()).markUploading(any(), any());
        verify(downloadSaveService, never()).stash(any());
        verify(downloadTaskRepo, never()).updateFailure(anyLong(), any(), anyString(), any());
    }

    /**
     * 检查点 phase=UPLOADING（陈旧）：采集既有产物后重新上传，并写 UPLOADED。
     *
     * @throws IOException 创建产物文件失败
     */
    @Test
    void shouldReuploadWhenUploadingCheckpointStale() throws IOException {
        P2pTaskState state = uploadingState();
        when(p2pTaskCoordinator.loadResumable(any())).thenReturn(state);
        when(p2pTaskCoordinator.awaitUploadSettled(any(), any())).thenReturn(Optional.of(state));
        when(p2pTaskCoordinator.collectExisting(eq(state), any()))
                .thenReturn(new P2pTaskResult(state, artifacts()));
        when(downloadSaveService.stash(any()))
                .thenReturn(new DownloadSaveService.StashResult("saved.mp4", 5L));

        handler.execute(execution);

        verify(p2pTaskCoordinator).markUploading(any(), eq(state));
        verify(p2pTaskCoordinator).markUploaded(any(), eq(state), eq("saved.mp4"), eq(5L));
        verify(downloadTaskRepo).updateFinish(eq(100L), eq(DownloadTaskConstants.Status.FINISH),
                eq(5L), eq(5L), any(), any(Date.class));
        verify(p2pTaskCoordinator).finishSuccess(any(), eq(state));
    }

    /**
     * 检查点 phase=UPLOADING，但等待期间已变为 UPLOADED：直接补报完成，不重复上传。
     */
    @Test
    void shouldReportFinishWhenUploadSettledToUploaded() {
        P2pTaskState uploading = uploadingState();
        P2pTaskState uploaded = uploadedState();
        when(p2pTaskCoordinator.loadResumable(any())).thenReturn(uploading);
        when(p2pTaskCoordinator.awaitUploadSettled(any(), any())).thenReturn(Optional.of(uploaded));

        handler.execute(execution);

        verify(downloadTaskRepo).updateFinish(eq(100L), eq(DownloadTaskConstants.Status.FINISH),
                eq(1024L), eq(1024L), eq("x.bin"), any(Date.class));
        verify(p2pTaskCoordinator, never()).collectExisting(any(), any());
        verify(downloadSaveService, never()).stash(any());
        verify(p2pTaskCoordinator).finishSuccess(any(), eq(uploaded));
    }

    /**
     * 上传阶段收到挂起：允许上传完成并写 UPLOADED，然后抛挂起异常（等待接管实例补报，不写终态）。
     *
     * @throws IOException 创建产物文件失败
     */
    @Test
    void shouldDetachAfterUploadInsteadOfReportingFinish() throws IOException {
        P2pTaskState state = uploadingState();
        AtomicBoolean detachRequested = new AtomicBoolean(false);
        when(execution.isDetachRequested()).thenAnswer(invocation -> detachRequested.get());
        when(p2pTaskCoordinator.loadResumable(any())).thenReturn(state);
        when(p2pTaskCoordinator.awaitUploadSettled(any(), any())).thenReturn(Optional.of(state));
        when(p2pTaskCoordinator.collectExisting(eq(state), any()))
                .thenReturn(new P2pTaskResult(state, artifacts()));
        when(downloadSaveService.stash(any())).thenAnswer(invocation -> {
            detachRequested.set(true);
            return new DownloadSaveService.StashResult("saved.mp4", 5L);
        });

        assertThrows(SfcTaskDetachedException.class, () -> handler.execute(execution));

        verify(p2pTaskCoordinator).markUploaded(any(), eq(state), eq("saved.mp4"), eq(5L));
        verify(downloadTaskRepo, never()).updateFinish(anyLong(), any(), anyLong(), anyLong(), any(), any());
        verify(downloadTaskRepo, never()).updateFailure(anyLong(), any(), anyString(), any());
    }

    /**
     * 下载失败：回写 FAILED 并按收口矩阵清理（协调器读取上下文中挂载的检查点）。
     */
    @Test
    void shouldReleaseOnFailure() {
        P2pTaskState state = downloadingState();
        when(p2pTaskCoordinator.loadResumable(any())).thenReturn(null);
        doAnswer(invocation -> {
            DownloadContext context = invocation.getArgument(0);
            context.attachP2pState(state);
            throw new SfcJsonException(502, "aria2 下载失败");
        }).when(p2pTaskCoordinator).start(any());

        assertThrows(SfcJsonException.class, () -> handler.execute(execution));

        verify(downloadTaskRepo).updateFailure(eq(100L), eq(DownloadTaskConstants.Status.FAILED),
                anyString(), any(Date.class));
        verify(p2pTaskCoordinator).releaseOnFailure(any(), eq(state));
    }

    /**
     * 任务被取消：回写 CANCEL 并按收口矩阵清理。
     */
    @Test
    void shouldReleaseOnCancel() {
        P2pTaskState state = downloadingState();
        when(p2pTaskCoordinator.loadResumable(any())).thenReturn(null);
        doAnswer(invocation -> {
            DownloadContext context = invocation.getArgument(0);
            context.attachP2pState(state);
            throw new SfcTaskInterruptedException("用户取消");
        }).when(p2pTaskCoordinator).start(any());

        assertThrows(SfcTaskInterruptedException.class, () -> handler.execute(execution));

        verify(downloadTaskRepo).updateStatus(100L, DownloadTaskConstants.Status.CANCEL);
        verify(p2pTaskCoordinator).releaseOnCancel(any(), eq(state));
        verify(downloadTaskRepo, never()).updateFailure(anyLong(), any(), anyString(), any());
    }

    /**
     * 新建 P2P 任务：协调器完成后正常上传、写 UPLOADED 并收口。
     *
     * @throws IOException 创建产物文件失败
     */
    @Test
    void shouldRunNewP2pTaskNormally() throws IOException {
        P2pTaskState state = downloadedState();
        when(p2pTaskCoordinator.loadResumable(any())).thenReturn(null);
        when(p2pTaskCoordinator.start(any()))
                .thenReturn(new P2pTaskResult(state, artifacts()));
        when(downloadSaveService.stash(any()))
                .thenReturn(new DownloadSaveService.StashResult("saved.mp4", 5L));

        handler.execute(execution);

        verify(p2pTaskCoordinator).markUploading(any(), eq(state));
        verify(p2pTaskCoordinator).markUploaded(any(), eq(state), eq("saved.mp4"), eq(5L));
        verify(downloadTaskRepo).updateFinish(eq(100L), eq(DownloadTaskConstants.Status.FINISH),
                eq(5L), eq(5L), any(), any(Date.class));
        verify(p2pTaskCoordinator).finishSuccess(any(), eq(state));
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造 UPLOADED 阶段的检查点。
     *
     * @return 检查点
     */
    private P2pTaskState uploadedState() {
        P2pTaskState state = new P2pTaskState();
        state.setDownloadId(100L);
        state.setPhase(P2pTaskState.Phase.UPLOADED);
        state.setUploaded(true);
        state.setSavedName("x.bin");
        state.setSavedBytes(1024L);
        state.setLocalDir(tempRoot.resolve("100").toString());
        state.setAria2Dir("/downloads/100");
        state.replaceGids(List.of("g1"));
        return state;
    }

    /**
     * 构造 UPLOADING 阶段的检查点（时间戳陈旧）。
     *
     * @return 检查点
     */
    private P2pTaskState uploadingState() {
        P2pTaskState state = new P2pTaskState();
        state.setDownloadId(100L);
        state.setPhase(P2pTaskState.Phase.UPLOADING);
        state.setUploadStartedAt(System.currentTimeMillis() - 20 * 60_000L);
        state.setLocalDir(tempRoot.resolve("100").toString());
        state.setAria2Dir("/downloads/100");
        state.replaceGids(List.of("g1"));
        return state;
    }

    /**
     * 构造下载中的检查点。
     *
     * @return 检查点
     */
    private P2pTaskState downloadingState() {
        P2pTaskState state = new P2pTaskState();
        state.setDownloadId(100L);
        state.setPhase(P2pTaskState.Phase.DOWNLOADING);
        state.setLocalDir(tempRoot.resolve("100").toString());
        state.setAria2Dir("/downloads/100");
        state.replaceGids(List.of("g1"));
        return state;
    }

    /**
     * 构造下载完成的检查点（未登记做种）。
     *
     * @return 检查点
     */
    private P2pTaskState downloadedState() {
        P2pTaskState state = downloadingState();
        state.setPhase(P2pTaskState.Phase.DOWNLOADED);
        return state;
    }

    /**
     * 构造可供落盘服务使用的产物列表（真实临时文件）。
     *
     * @return 产物列表
     * @throws IOException 创建文件失败
     */
    private List<DownloadArtifact> artifacts() throws IOException {
        Path dir = Files.createDirectories(tempRoot.resolve("artifacts"));
        Path file = Files.writeString(dir.resolve("a.mp4"), "hello");
        return List.of(DownloadArtifact.of(dir, file));
    }
}
