package com.sfc.download.engine.p2p;

import com.sfc.cloud.task.worker.SfcTaskDetachedException;
import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.cloud.task.worker.SfcTaskProgress;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.download.config.Aria2Properties;
import com.sfc.download.config.SfcDownloadConfigService;
import com.sfc.download.config.SfcDownloadInternalProperties;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.model.property.Aria2Property;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link Aria2DownloadEngine} 会话式能力的轻量单测：gid 存活判定、磁力链 gid 跟随、检查点回调与挂起检测。
 *
 * <p>不启动 Spring、不连接真实 aria2；aria2 RPC 由 Mockito 桩定。</p>
 */
@ExtendWith(MockitoExtension.class)
class Aria2DownloadEngineSessionTest {

    /**
     * aria2 RPC 客户端。
     */
    @Mock
    private Aria2Client aria2Client;

    /**
     * 统一配置服务（本测试用固定快照）。
     */
    @Mock
    private SfcDownloadConfigService configService;

    /**
     * aria2 共享工作区。
     */
    @Mock
    private Aria2Workspace aria2Workspace;

    /**
     * P2P 协调器提供者（本测试不触发 execute 兼容入口）。
     */
    @Mock
    private ObjectProvider<P2pTaskCoordinator> coordinatorProvider;

    /**
     * 被测引擎。
     */
    private Aria2DownloadEngine engine;

    /**
     * Worker 任务执行上下文（Mock）。
     */
    private SfcTaskExecution execution;

    /**
     * 被测任务参数。
     */
    private DownloadTaskParams params;

    /**
     * 测试用临时目录（任务目录）。
     */
    @TempDir
    Path tempRoot;

    /**
     * 初始化引擎与执行上下文桩。
     */
    @BeforeEach
    void setUp() {
        Aria2Property aria2Config = new Aria2Property();
        aria2Config.setPollInterval(1);
        aria2Config.setStallTimeout(0);
        lenient().when(configService.getAria2()).thenReturn(aria2Config);

        engine = new Aria2DownloadEngine(aria2Client, new Aria2Properties(), configService,
                aria2Workspace, new SfcDownloadInternalProperties(), coordinatorProvider);

        execution = mock(SfcTaskExecution.class);
        lenient().when(execution.getProgress()).thenReturn(new SfcTaskProgress());
        lenient().when(execution.isInterruptRequested()).thenReturn(false);
        lenient().when(execution.isDetachRequested()).thenReturn(false);
        params = new DownloadTaskParams();
        params.downloadId = 100L;
        params.type = DownloadType.MAGNET;
        params.uid = 1L;
        params.savePath = "/";
    }

    /**
     * gid 存活判定：存在且未终止算存活；协议错误算丢失；传输层失败必须向上抛（不能当成丢失）。
     */
    @Test
    void shouldFilterAliveGidsCorrectly() {
        when(aria2Client.tellStatus("alive", List.of("status"))).thenReturn(Map.of("status", "active"));
        when(aria2Client.tellStatus("removed", List.of("status"))).thenReturn(Map.of("status", "removed"));
        when(aria2Client.tellStatus("error", List.of("status"))).thenReturn(Map.of("status", "error"));
        when(aria2Client.tellStatus("lost", List.of("status")))
                .thenThrow(new Aria2RpcException(1, "GID#lost is not found"));

        List<String> alive = engine.filterAliveGids(List.of("alive", "removed", "error", "lost", " "));

        assertEquals(List.of("alive"), alive);

        when(aria2Client.tellStatus("down", List.of("status")))
                .thenThrow(new Aria2RpcException(Aria2RpcException.UNKNOWN_CODE, "Connection refused"));
        assertThrows(Aria2RpcException.class, () -> engine.filterAliveGids(List.of("down")),
                "aria2 不可达时不能把 gid 判定为丢失（否则会重复提交下载）");
    }

    /**
     * 空 gid 列表直接返回空。
     */
    @Test
    void shouldReturnEmptyAliveGidsForEmptyInput() {
        assertTrue(engine.filterAliveGids(List.of()).isEmpty());
        assertTrue(engine.filterAliveGids(null).isEmpty());
    }

    /**
     * 磁力链两阶段跟随：元数据 gid complete → followedBy 内容 gid → 内容 gid complete；会话与回调同步更新。
     */
    @Test
    void shouldFollowMagnetContentGidAndNotifySessionListener() {
        String infohash = "08ada5a7a6183aae1e09d831df6748d566095a10";
        when(aria2Client.tellStatus(eq("meta"), any()))
                .thenReturn(Map.of("status", "complete", "followedBy", List.of("content")));
        when(aria2Client.tellStatus(eq("content"), any()))
                .thenReturn(Map.of(
                        "status", "complete",
                        "totalLength", "2048",
                        "files", List.of(Map.of("path", "/tmp/x/a.bin", "length", "2048")),
                        "bittorrent", Map.of("infoHash", infohash)));

        DownloadContext context = new DownloadContext(execution, params);
        Aria2TaskSession session = new Aria2TaskSession("meta");
        StringBuilder changedGids = new StringBuilder();

        Map<String, Object> status = engine.awaitCompletion(context, session, changed -> {
            changedGids.append(String.join(",", changed.gids()));
            changedGids.append(';');
        });

        assertEquals("complete", status.get("status"));
        assertEquals(List.of("meta", "content"), List.copyOf(session.gids()));
        assertEquals(List.of("content"), List.copyOf(session.contentGids()));
        assertEquals(infohash, session.infohash());
        assertTrue(changedGids.toString().contains("content"), "跟随 gid 后应通知会话变化以保存检查点");
    }

    /**
     * 无跟随链的任务完成时，提交 gid 即内容 gid。
     */
    @Test
    void shouldTreatPrimaryGidAsContentGidWhenNoFollow() {
        when(aria2Client.tellStatus(eq("g1"), any())).thenReturn(Map.of(
                "status", "complete",
                "totalLength", "10",
                "files", List.of(Map.of("path", "/tmp/x/a.bin", "length", "10"))));

        DownloadContext context = new DownloadContext(execution, params);
        Aria2TaskSession session = new Aria2TaskSession("g1");

        engine.awaitCompletion(context, session, null);

        assertEquals(List.of("g1"), List.copyOf(session.contentGids()));
    }

    /**
     * 做种中的 active gid（seeder=true 且内容完整）必须按「下载完成」处理，而不是继续等待 complete。
     *
     * <p>回归 P5-D01：默认 {@code download.aria2.seed-time-minutes=10080} 时，种子下载完成后 aria2 仍是
     * {@code active} 并做种；旧实现只认 {@code complete}，导致任务卡在 DOWNLOADING 直到撞无进度超时。</p>
     */
    @Test
    void shouldTreatSeedingActiveGidAsFinished() {
        when(aria2Client.tellStatus(eq("g1"), any())).thenReturn(Map.of(
                "status", "active",
                "seeder", "true",
                "completedLength", "2048",
                "totalLength", "2048",
                "files", List.of(Map.of("path", "/tmp/x/a.bin", "length", "2048"))));
        DownloadContext context = new DownloadContext(execution, params);
        Aria2TaskSession session = new Aria2TaskSession("g1");

        Map<String, Object> status = engine.awaitCompletion(context, session, null);

        assertEquals("active", status.get("status"), "返回的完成状态即最后一次轮询快照");
        assertEquals(List.of("g1"), List.copyOf(session.contentGids()));
        verify(aria2Client).tellStatus(eq("g1"), any());
    }

    /**
     * 做种中的 active gid 但内容尚未完整（completed &lt; total）不能提前判定完成。
     */
    @Test
    void shouldNotTreatIncompleteSeedingGidAsFinished() {
        when(aria2Client.tellStatus(eq("g1"), any()))
                .thenReturn(Map.of("status", "active", "seeder", "true",
                        "completedLength", "1024", "totalLength", "2048"))
                .thenReturn(Map.of("status", "error", "errorCode", "1", "errorMessage", "download failed"));
        DownloadContext context = new DownloadContext(execution, params);
        Aria2TaskSession session = new Aria2TaskSession("g1");

        assertThrows(com.sfc.common.exception.SfcJsonException.class,
                () -> engine.awaitCompletion(context, session, null));
        assertTrue(session.contentGids().isEmpty());
    }

    /**
     * infohash 从 tellStatus 顶层 {@code infoHash} 字段提取（真实 aria2 返回位置；bittorrent 对象里没有）。
     */
    @Test
    void shouldExtractTopLevelInfoHash() {
        String infohash = "08ADA5A7A6183AAE1E09D831DF6748D566095A10";
        when(aria2Client.tellStatus(eq("g1"), any())).thenReturn(Map.of(
                "status", "complete",
                "totalLength", "10",
                "infoHash", infohash,
                "bittorrent", Map.of("info", Map.of("name", "test")),
                "files", List.of(Map.of("path", "/tmp/x/a.bin", "length", "10"))));
        DownloadContext context = new DownloadContext(execution, params);
        Aria2TaskSession session = new Aria2TaskSession("g1");

        engine.awaitCompletion(context, session, null);

        assertEquals(infohash.toLowerCase(), session.infohash());
    }

    /**
     * URL 形式的种子任务：{@link Aria2DownloadEngine#ensureTorrentBase64(DownloadContext)} 拉取并回填
     * {@code torrentBase64}，且已回填后不重复拉取。
     *
     * @throws IOException 启动本地 HTTP 服务失败
     */
    @Test
    void shouldBackfillTorrentBase64ForUrlTorrent() throws IOException {
        byte[] torrentBytes = "d4:infod4:name4:testee".getBytes(StandardCharsets.UTF_8);
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/a.torrent", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, torrentBytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(torrentBytes);
            }
        });
        server.start();
        try {
            params.type = DownloadType.TORRENT;
            params.url = "http://127.0.0.1:" + server.getAddress().getPort() + "/a.torrent";
            DownloadContext context = new DownloadContext(execution, params);

            engine.ensureTorrentBase64(context);
            engine.ensureTorrentBase64(context);

            assertArrayEquals(torrentBytes, Base64.getDecoder().decode(params.torrentBase64));
            assertEquals(1, hits.get(), "已回填种子内容后不应重复拉取");
        } finally {
            server.stop(0);
        }
    }

    /**
     * 提交阶段 aria2 报 infohash 重复注册时，转换为 409 可读业务异常。
     */
    @Test
    void shouldTranslateDuplicateInfoHashAsConflict() {
        params.type = DownloadType.MAGNET;
        params.url = "magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10";
        when(aria2Client.addUri(any(), any()))
                .thenThrow(new Aria2RpcException(1, "InfoHash 08ada5a7a6183aae1e09d831df6748d566095a10 is already registered"));

        SfcJsonException exception = assertThrows(SfcJsonException.class,
                () -> engine.submitSession(newContext()));

        assertEquals(409, exception.getRes().getCode());
        assertTrue(exception.getMessage().contains("做种保留中"));
    }

    /**
     * 检测到挂起请求时抛 {@link SfcTaskDetachedException}，且不再向 aria2 发轮询请求。
     */
    @Test
    void shouldThrowDetachedBeforePolling() {
        when(execution.isDetachRequested()).thenReturn(true);
        DownloadContext context = new DownloadContext(execution, params);
        Aria2TaskSession session = new Aria2TaskSession("g1");

        assertThrows(SfcTaskDetachedException.class, () -> engine.awaitCompletion(context, session, null));
        verify(aria2Client, never()).tellStatus(anyString(), any());
    }

    /**
     * 丢弃 aria2 任务结果：依次 forceRemove + removeDownloadResult 并跳过空白 gid。
     */
    @Test
    void shouldDiscardTaskResults() {
        engine.discardTaskResults(List.of("g1", " ", "g2"));

        verify(aria2Client).forceRemove("g1");
        verify(aria2Client).removeDownloadResult("g1");
        verify(aria2Client).forceRemove("g2");
        verify(aria2Client).removeDownloadResult("g2");
        verify(aria2Client, never()).forceRemove("");
    }

    /**
     * 从已跟踪 gid 重建会话；空列表时报业务异常。
     */
    @Test
    void shouldResumeSessionFromTrackedGids() {
        DownloadContext context = new DownloadContext(execution, params);

        Aria2TaskSession session = engine.resumeSession(context, List.of("g1", "g2"));

        assertEquals("g1", session.primaryGid());
        assertEquals(List.of("g1", "g2"), List.copyOf(session.gids()));
        assertFalse(session.gids().isEmpty());
    }

    /**
     * 构造带真实任务目录的执行上下文（用于需要 {@code getTempDir()} 的提交类测试）。
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
}
