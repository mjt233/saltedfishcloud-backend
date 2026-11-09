package com.sfc.download.engine.p2p;

import com.sfc.download.config.SfcDownloadInternalProperties;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.model.po.DownloadTaskInfo;
import com.sfc.download.repo.DownloadTaskRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link Aria2OrphanReconciler} 决策测试：工作区外不动、终态清理、做种保护跳过、记录不存在只 WARN 不删。
 */
@ExtendWith(MockitoExtension.class)
class Aria2OrphanReconcilerTest {

    /**
     * aria2 RPC 客户端。
     */
    @Mock
    private Aria2Client aria2Client;

    /**
     * aria2 共享工作区。
     */
    @Mock
    private Aria2Workspace aria2Workspace;

    /**
     * 下载任务仓储。
     */
    @Mock
    private DownloadTaskRepo downloadTaskRepo;

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
     * 测试用目录（模拟本服务工作区中的任务目录）。
     */
    @TempDir
    Path tempRoot;

    /**
     * 对账器实例。
     */
    private Aria2OrphanReconciler reconciler;

    /**
     * 初始化对账器与通用桩。
     */
    @BeforeEach
    void setUp() {
        SfcDownloadInternalProperties properties = new SfcDownloadInternalProperties();
        lenient().when(seedRegistrarProvider.getIfAvailable(anySupplier())).thenReturn(seedRegistrar);
        reconciler = new Aria2OrphanReconciler(aria2Client, aria2Workspace, downloadTaskRepo,
                seedRegistrarProvider, properties);
        lenient().when(aria2Workspace.validate()).thenReturn(null);
        lenient().when(aria2Client.tellWaiting(anyInt(), anyInt(), any())).thenReturn(List.of());
        lenient().when(aria2Client.tellStopped(anyInt(), anyInt(), any())).thenReturn(List.of());
        lenient().when(aria2Workspace.isInWorkspace(any())).thenReturn(true);
    }

    /**
     * aria2 不可达：本轮不执行，不查库也不清理。
     */
    @Test
    void shouldSkipRoundWhenAria2Unreachable() {
        when(aria2Client.tellActive(any())).thenThrow(new Aria2RpcException(-1, "连接被拒绝"));

        Aria2OrphanReconciler.ReconcileSummary summary = reconciler.reconcileOnce();

        assertFalse(summary.executed());
        assertTrue(summary.skipReason().contains("aria2"));
        verifyNoInteractions(downloadTaskRepo);
    }

    /**
     * 共享目录配置无效：本轮不执行。
     */
    @Test
    void shouldSkipRoundWhenWorkspaceInvalid() {
        when(aria2Workspace.validate()).thenReturn("shared-dir 不存在");

        Aria2OrphanReconciler.ReconcileSummary summary = reconciler.reconcileOnce();

        assertFalse(summary.executed());
        verifyNoInteractions(aria2Client);
    }

    /**
     * 工作区之外的 aria2 任务绝不触碰。
     */
    @Test
    void shouldNeverTouchTasksOutsideWorkspace() {
        when(aria2Client.tellActive(any())).thenReturn(List.of(status("g-out", "/downloads/other/1-x")));
        when(aria2Workspace.toLocalDir("/downloads/other/1-x")).thenReturn(Path.of("/tmp/other/1-x"));
        when(aria2Workspace.isInWorkspace(any())).thenReturn(false);

        Aria2OrphanReconciler.ReconcileSummary summary = reconciler.reconcileOnce();

        assertTrue(summary.executed());
        assertEquals(1, summary.skipped());
        assertEquals(0, summary.cleaned());
        verify(aria2Client, never()).forceRemove(anyString());
        verifyNoInteractions(downloadTaskRepo);
    }

    /**
     * 下载任务记录不存在：只记 WARN，不删除目录、不移除 aria2 任务。
     *
     * @throws IOException 创建目录失败
     */
    @Test
    void shouldNotDeleteWhenDownloadTaskMissing() throws IOException {
        Path taskDir = Files.createDirectory(tempRoot.resolve("404-uuid"));
        stubCandidate("g-missing", taskDir, 404L);
        when(downloadTaskRepo.findById(404L)).thenReturn(Optional.empty());

        Aria2OrphanReconciler.ReconcileSummary summary = reconciler.reconcileOnce();

        assertEquals(0, summary.cleaned());
        verify(aria2Client, never()).forceRemove(anyString());
        assertTrue(Files.isDirectory(taskDir), "记录不存在时不能删除目录");
    }

    /**
     * 运行中 / 等待中的任务可能等待接管，必须跳过。
     *
     * @throws IOException 创建目录失败
     */
    @Test
    void shouldSkipRunningAndWaitingTasks() throws IOException {
        Path runningDir = Files.createDirectory(tempRoot.resolve("1-uuid"));
        Path waitingDir = Files.createDirectory(tempRoot.resolve("2-uuid"));
        stubCandidate("g-running", runningDir, 1L);
        when(aria2Client.tellActive(any())).thenReturn(List.of(
                status("g-running", "/downloads/running"), status("g-waiting", "/downloads/waiting")));
        when(aria2Workspace.toLocalDir("/downloads/waiting")).thenReturn(waitingDir);
        when(aria2Workspace.parseDownloadId(runningDir)).thenReturn(1L);
        when(aria2Workspace.parseDownloadId(waitingDir)).thenReturn(2L);
        when(downloadTaskRepo.findById(1L)).thenReturn(Optional.of(task(1L, DownloadTaskConstants.Status.RUNNING)));
        when(downloadTaskRepo.findById(2L)).thenReturn(Optional.of(task(2L, DownloadTaskConstants.Status.WAITING)));

        Aria2OrphanReconciler.ReconcileSummary summary = reconciler.reconcileOnce();

        assertEquals(0, summary.cleaned());
        verify(aria2Client, never()).forceRemove(anyString());
        assertTrue(Files.isDirectory(runningDir));
        assertTrue(Files.isDirectory(waitingDir));
    }

    /**
     * 终态任务且不在做种保护中：移除 aria2 任务结果并删除目录。
     *
     * @throws IOException 创建目录失败
     */
    @Test
    void shouldCleanTerminalTask() throws IOException {
        Path taskDir = Files.createDirectory(tempRoot.resolve("9-uuid"));
        Files.writeString(taskDir.resolve("a.bin"), "x");
        stubCandidate("g-done", taskDir, 9L);
        when(downloadTaskRepo.findById(9L)).thenReturn(Optional.of(task(9L, DownloadTaskConstants.Status.FINISH)));
        when(seedRegistrar.isProtectedDirectory(taskDir)).thenReturn(false);

        Aria2OrphanReconciler.ReconcileSummary summary = reconciler.reconcileOnce();

        assertEquals(1, summary.cleaned());
        verify(aria2Client).forceRemove("g-done");
        verify(aria2Client).removeDownloadResult("g-done");
        assertFalse(Files.exists(taskDir), "终态任务目录应被删除");
    }

    /**
     * 做种保护中的目录：跳过，不移除 aria2 任务、不删目录。
     *
     * @throws IOException 创建目录失败
     */
    @Test
    void shouldSkipProtectedDirectory() throws IOException {
        Path taskDir = Files.createDirectory(tempRoot.resolve("10-uuid"));
        stubCandidate("g-seeding", taskDir, 10L);
        when(downloadTaskRepo.findById(10L)).thenReturn(Optional.of(task(10L, DownloadTaskConstants.Status.FINISH)));
        when(seedRegistrar.isProtectedDirectory(taskDir)).thenReturn(true);

        Aria2OrphanReconciler.ReconcileSummary summary = reconciler.reconcileOnce();

        assertEquals(0, summary.cleaned());
        verify(aria2Client, never()).forceRemove(anyString());
        assertTrue(Files.isDirectory(taskDir));
    }

    /**
     * 做种保护查询失败（数据库异常）：按 fail-safe 跳过该候选，不删除。
     *
     * @throws IOException 创建目录失败
     */
    @Test
    void shouldSkipCandidateWhenProtectionQueryFails() throws IOException {
        Path taskDir = Files.createDirectory(tempRoot.resolve("11-uuid"));
        stubCandidate("g-error", taskDir, 11L);
        when(downloadTaskRepo.findById(11L)).thenReturn(Optional.of(task(11L, DownloadTaskConstants.Status.FAILED)));
        when(seedRegistrar.isProtectedDirectory(taskDir)).thenThrow(new IllegalStateException("database is locked"));

        Aria2OrphanReconciler.ReconcileSummary summary = reconciler.reconcileOnce();

        assertEquals(0, summary.cleaned());
        verify(aria2Client, never()).forceRemove(anyString());
        assertTrue(Files.isDirectory(taskDir));
    }

    /**
     * 目录名无法解析下载任务 id：跳过。
     */
    @Test
    void shouldSkipUnparsableDirectoryName() {
        when(aria2Client.tellActive(any())).thenReturn(List.of(status("g-unknown", "/downloads/unknown")));
        when(aria2Workspace.toLocalDir("/downloads/unknown")).thenReturn(tempRoot.resolve("no-id-dir"));
        when(aria2Workspace.parseDownloadId(any())).thenReturn(null);

        Aria2OrphanReconciler.ReconcileSummary summary = reconciler.reconcileOnce();

        assertEquals(0, summary.cleaned());
        verify(downloadTaskRepo, never()).findById(anyLong());
        verify(aria2Client, never()).forceRemove(anyString());
    }

    /**
     * 桩定单个候选任务（active 列表 + 目录映射 + downloadId 解析）。
     *
     * @param gid        aria2 gid
     * @param localDir   本服务侧任务目录
     * @param downloadId 解析出的下载任务 id
     */
    private void stubCandidate(String gid, Path localDir, Long downloadId) {
        String aria2Dir = "/downloads/" + localDir.getFileName();
        when(aria2Client.tellActive(any())).thenReturn(List.of(status(gid, aria2Dir)));
        when(aria2Workspace.toLocalDir(aria2Dir)).thenReturn(localDir);
        lenient().when(aria2Workspace.isInWorkspace(localDir)).thenReturn(true);
        when(aria2Workspace.parseDownloadId(localDir)).thenReturn(downloadId);
    }

    /**
     * 构造 aria2 状态快照。
     *
     * @param gid aria2 任务标识
     * @param dir aria2 下载目录
     * @return 状态快照
     */
    private static Map<String, Object> status(String gid, String dir) {
        return Map.of("gid", gid, "dir", dir, "status", "active");
    }

    /**
     * 构造下载任务记录。
     *
     * @param id     下载任务 id
     * @param status 状态镜像
     * @return 下载任务记录
     */
    private static DownloadTaskInfo task(Long id, Integer status) {
        DownloadTaskInfo task = new DownloadTaskInfo();
        task.setId(id);
        task.setStatus(status);
        return task;
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
