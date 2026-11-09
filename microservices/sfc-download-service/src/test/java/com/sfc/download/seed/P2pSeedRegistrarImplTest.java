package com.sfc.download.seed;

import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.download.config.SfcDownloadConfigService;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.engine.p2p.P2pSeedReuseTarget;
import com.sfc.download.engine.p2p.P2pTaskState;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.model.po.DownloadSeedTask;
import com.sfc.download.model.property.Aria2Property;
import com.sfc.download.repo.DownloadSeedTaskRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link P2pSeedRegistrarImpl} 单元测试：做种登记（含 0 配置与重复登记）、同 infohash / 同种子复用查询、
 * 目录保护查询。
 */
@ExtendWith(MockitoExtension.class)
class P2pSeedRegistrarImplTest {

    /**
     * 测试用 infohash（40 位小写 hex）。
     */
    private static final String INFO_HASH = "08ada5a7a6183aae1e09d831df6748d566095a10";

    /**
     * 测试用种子内容（{@code hello torrent}）。
     */
    private static final String TORRENT_BASE64 = "aGVsbG8gdG9ycmVudA==";

    /**
     * 上述种子内容的 SHA-256。
     */
    private static final String TORRENT_SHA256 =
            "ecb9cc0a989397c1b304da9407a12ed0fc01530a64b0d14f1001d06326c91534";

    /**
     * 测试用下载任务 id。
     */
    private static final Long TEST_DOWNLOAD_ID = 100L;

    /**
     * 做种记录仓储替身。
     */
    @Mock
    private DownloadSeedTaskRepo seedTaskRepo;

    /**
     * 统一配置服务替身。
     */
    @Mock
    private SfcDownloadConfigService configService;

    /**
     * 被测对象。
     */
    @InjectMocks
    private P2pSeedRegistrarImpl registrar;

    /**
     * 测试用临时目录。
     */
    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("做种时长为 0：不登记做种记录，返回 false")
    void registerShouldReturnFalseWhenSeedTimeDisabled() {
        when(configService.getAria2()).thenReturn(aria2Property(0));
        DownloadTaskParams params = magnetParams(100L);
        DownloadContext context = context(params);
        P2pTaskState state = new P2pTaskState();
        state.setDownloadId(100L);
        state.setLocalDir(tempDir.toString());

        assertFalse(registrar.register(context, state, List.of()));
        assertFalse(state.isSeeding());
        verify(seedTaskRepo, never()).save(any());
    }

    @Test
    @DisplayName("登记成功：写做种记录并就地回写 seeding / seedTaskId / seedEndAt")
    void registerShouldPersistRecordAndWriteBackCheckpoint() throws IOException {
        Path seedDir = Files.createDirectories(tempDir.resolve("seed-dir"));
        when(configService.getAria2()).thenReturn(aria2Property(60));
        DownloadTaskParams params = magnetParams(100L);
        DownloadContext context = context(params);
        P2pTaskState state = magnetState(seedDir.toString());
        when(seedTaskRepo.findByDownloadId(100L)).thenReturn(Optional.empty());
        when(seedTaskRepo.save(any(DownloadSeedTask.class))).thenAnswer(invocation -> {
            DownloadSeedTask saved = invocation.getArgument(0);
            saved.setId(999L);
            return saved;
        });

        assertTrue(registrar.register(context, state, List.of()));

        ArgumentCaptor<DownloadSeedTask> captor = ArgumentCaptor.forClass(DownloadSeedTask.class);
        verify(seedTaskRepo).save(captor.capture());
        DownloadSeedTask saved = captor.getValue();
        assertEquals(100L, saved.getDownloadId());
        assertEquals(SeedTaskStatus.SEEDING, saved.getStatus());
        assertEquals(INFO_HASH, saved.getInfohash());
        assertEquals("gid-content", saved.getContentGid());
        assertEquals("/downloads/task", saved.getAria2Dir());
        assertEquals(seedDir.toString(), saved.getLocalDir());
        assertEquals(7L, saved.getUid());
        long seedDurationMillis = saved.getSeedEndAt().getTime() - saved.getSeedStartAt().getTime();
        assertEquals(TimeUnit.MINUTES.toMillis(60), seedDurationMillis);

        assertTrue(state.isSeeding());
        assertEquals(999L, state.getSeedTaskId());
        assertEquals(saved.getSeedEndAt().getTime(), state.getSeedEndAt());
        verify(context.getExecution()).log(argThat(message -> message != null
                && message.contains("已登记做种保留")));
    }

    @Test
    @DisplayName("重复登记：已存在「做种中」记录时复用原记录与到期时间，不延长做种时长")
    void registerShouldReuseExistingSeedingRecord() {
        when(configService.getAria2()).thenReturn(aria2Property(60));
        Date existingEndAt = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        DownloadSeedTask existing = new DownloadSeedTask();
        existing.setId(88L);
        existing.setDownloadId(100L);
        existing.setStatus(SeedTaskStatus.SEEDING);
        existing.setSeedEndAt(existingEndAt);
        when(seedTaskRepo.findByDownloadId(100L)).thenReturn(Optional.of(existing));
        DownloadTaskParams params = magnetParams(100L);
        DownloadContext context = context(params);
        P2pTaskState state = magnetState(tempDir.toString());

        assertTrue(registrar.register(context, state, List.of()));

        verify(seedTaskRepo, never()).save(any());
        assertEquals(88L, state.getSeedTaskId());
        assertEquals(existingEndAt.getTime(), state.getSeedEndAt());
    }

    @Test
    @DisplayName("重复登记：已存在记录不是「做种中」（已清理）时返回 false")
    void registerShouldReturnFalseWhenExistingRecordNotSeeding() {
        when(configService.getAria2()).thenReturn(aria2Property(60));
        DownloadSeedTask existing = new DownloadSeedTask();
        existing.setId(88L);
        existing.setDownloadId(100L);
        existing.setStatus(SeedTaskStatus.CLEANED);
        when(seedTaskRepo.findByDownloadId(100L)).thenReturn(Optional.of(existing));
        DownloadContext context = context(magnetParams(100L));
        P2pTaskState state = magnetState(tempDir.toString());

        assertFalse(registrar.register(context, state, List.of()));
        assertFalse(state.isSeeding());
        verify(seedTaskRepo, never()).save(any());
    }

    @Test
    @DisplayName("并发/崩溃重试导致的唯一约束冲突：回查已存在记录并按已存在处理")
    void registerShouldHandleDuplicateInsert() {
        when(configService.getAria2()).thenReturn(aria2Property(60));
        DownloadSeedTask existing = new DownloadSeedTask();
        existing.setId(77L);
        existing.setDownloadId(100L);
        existing.setStatus(SeedTaskStatus.SEEDING);
        existing.setSeedEndAt(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)));
        when(seedTaskRepo.findByDownloadId(100L)).thenReturn(Optional.empty()).thenReturn(Optional.of(existing));
        when(seedTaskRepo.save(any(DownloadSeedTask.class)))
                .thenThrow(new DataIntegrityViolationException("UNIQUE constraint failed: download_seed_task.download_id"));
        DownloadContext context = context(magnetParams(100L));
        P2pTaskState state = magnetState(tempDir.toString());

        assertTrue(registrar.register(context, state, List.of()));
        assertEquals(77L, state.getSeedTaskId());
    }

    @Test
    @DisplayName("缺少下载任务 id：不登记做种记录")
    void registerShouldReturnFalseWhenDownloadIdMissing() {
        when(configService.getAria2()).thenReturn(aria2Property(60));
        DownloadTaskParams params = magnetParams(null);
        DownloadContext context = context(params);
        P2pTaskState state = new P2pTaskState();
        state.setLocalDir(tempDir.toString());

        assertFalse(registrar.register(context, state, List.of()));
        verify(seedTaskRepo, never()).save(any());
    }

    @Test
    @DisplayName("磁力复用：按 infohash 命中做种中且目录存在的记录")
    void findReusableShouldMatchMagnetInfoHash() throws IOException {
        Path seedDir = Files.createDirectories(tempDir.resolve("reusable"));
        Date seedEndAt = new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));
        DownloadSeedTask seed = seedRecord(66L, 666L, seedDir.toString());
        seed.setInfohash(INFO_HASH);
        seed.setSeedEndAt(seedEndAt);
        when(seedTaskRepo.findActiveByInfohash(eq(INFO_HASH), eq(SeedTaskStatus.SEEDING), any(Date.class)))
                .thenReturn(List.of(seed));

        Optional<P2pSeedReuseTarget> target = registrar.findReusable(magnetParams(101L));

        assertTrue(target.isPresent());
        assertEquals(66L, target.get().seedTaskId());
        assertEquals(666L, target.get().downloadId());
        assertEquals("gid-content", target.get().contentGid());
        assertEquals("/downloads/task", target.get().aria2Dir());
        assertEquals(seedDir.toAbsolutePath().normalize().toString(), target.get().localDir());
        assertEquals(seedEndAt, target.get().seedEndAt());
    }

    @Test
    @DisplayName("磁力复用：命中的记录本地目录不存在时返回 empty")
    void findReusableShouldReturnEmptyWhenLocalDirMissing() {
        DownloadSeedTask seed = seedRecord(66L, 666L, tempDir.resolve("not-exists").toString());
        seed.setInfohash(INFO_HASH);
        seed.setSeedEndAt(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)));
        when(seedTaskRepo.findActiveByInfohash(eq(INFO_HASH), eq(SeedTaskStatus.SEEDING), any(Date.class)))
                .thenReturn(List.of(seed));

        assertTrue(registrar.findReusable(magnetParams(101L)).isEmpty());
    }

    @Test
    @DisplayName("种子复用：按种子内容 SHA-256 命中记录")
    void findReusableShouldMatchTorrentSha256() throws IOException {
        Path seedDir = Files.createDirectories(tempDir.resolve("reusable-torrent"));
        DownloadSeedTask seed = seedRecord(55L, 555L, seedDir.toString());
        seed.setSeedEndAt(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)));
        when(seedTaskRepo.findByTorrentSha256(eq(TORRENT_SHA256), eq(SeedTaskStatus.SEEDING), any(Date.class)))
                .thenReturn(List.of(seed));
        DownloadTaskParams params = new DownloadTaskParams();
        params.type = DownloadType.TORRENT;
        params.url = "https://example.com/a.torrent";
        params.torrentBase64 = TORRENT_BASE64;
        params.downloadId = 102L;

        Optional<P2pSeedReuseTarget> target = registrar.findReusable(params);

        assertTrue(target.isPresent());
        assertEquals(55L, target.get().seedTaskId());
    }

    @Test
    @DisplayName("非 P2P 类型不查询做种记录")
    void findReusableShouldIgnoreNonP2pType() {
        DownloadTaskParams params = new DownloadTaskParams();
        params.type = DownloadType.HTTP;
        params.url = "https://example.com/a.zip";

        assertTrue(registrar.findReusable(params).isEmpty());
        verifyNoInteractions(seedTaskRepo);
    }

    @Test
    @DisplayName("目录保护：按绝对路径规范化后的文本查询")
    void isProtectedDirectoryShouldQueryNormalizedPath() {
        when(seedTaskRepo.existsByStatusAndLocalDir(eq(SeedTaskStatus.SEEDING), anyString())).thenReturn(true);

        assertTrue(registrar.isProtectedDirectory(tempDir));

        verify(seedTaskRepo).existsByStatusAndLocalDir(SeedTaskStatus.SEEDING,
                tempDir.toAbsolutePath().normalize().toString());
    }

    @Test
    @DisplayName("目录保护：精确匹配未命中时按生效中的做种目录规范化比对（兼容相对路径写法）")
    void isProtectedDirectoryShouldFallBackToActiveDirsComparison() {
        Path relativePath = Path.of("relative-seed-dir");
        String absoluteText = relativePath.toAbsolutePath().normalize().toString();
        when(seedTaskRepo.existsByStatusAndLocalDir(eq(SeedTaskStatus.SEEDING), anyString())).thenReturn(false);
        when(seedTaskRepo.findAllActiveLocalDirs(SeedTaskStatus.SEEDING)).thenReturn(List.of(absoluteText));

        assertTrue(registrar.isProtectedDirectory(relativePath));
    }

    @Test
    @DisplayName("目录保护：查询失败必须抛出异常（调用方 fail-safe 跳过本轮清理）")
    void isProtectedDirectoryShouldPropagateQueryFailure() {
        when(seedTaskRepo.existsByStatusAndLocalDir(eq(SeedTaskStatus.SEEDING), anyString()))
                .thenThrow(new IllegalStateException("database is locked"));

        assertThrows(IllegalStateException.class, () -> registrar.isProtectedDirectory(tempDir));
    }

    @Test
    @DisplayName("目录保护：null 目录直接返回 false")
    void isProtectedDirectoryShouldReturnFalseForNull() {
        assertFalse(registrar.isProtectedDirectory(null));
        verifyNoInteractions(seedTaskRepo);
    }

    // ==================== 测试辅助 ====================

    /**
     * 构造磁力链下载参数。
     *
     * @param downloadId 下载任务 id，允许为 {@code null}
     * @return 下载参数
     */
    private static DownloadTaskParams magnetParams(Long downloadId) {
        DownloadTaskParams params = new DownloadTaskParams();
        params.type = DownloadType.MAGNET;
        params.url = "magnet:?xt=urn:btih:" + INFO_HASH + "&dn=test";
        params.downloadId = downloadId;
        params.uid = 7L;
        return params;
    }

    /**
     * 构造携带完整 P2P 检查点的执行上下文。
     *
     * @param params 下载参数
     * @return 执行上下文（Worker 执行上下文为 Mockito 替身）
     */
    private static DownloadContext context(DownloadTaskParams params) {
        return new DownloadContext(mock(SfcTaskExecution.class), params);
    }

    /**
     * 构造磁力链任务的检查点状态（下载任务 id 固定为 {@value #TEST_DOWNLOAD_ID}）。
     *
     * @param localDir 本服务侧目录
     * @return 检查点状态
     */
    private static P2pTaskState magnetState(String localDir) {
        P2pTaskState state = new P2pTaskState();
        state.setDownloadId(TEST_DOWNLOAD_ID);
        state.setPhase(P2pTaskState.Phase.DOWNLOADED);
        state.setLocalDir(localDir);
        state.setAria2Dir("/downloads/task");
        state.setGids(List.of("gid-meta", "gid-content"));
        state.setContentGids(List.of("gid-content"));
        state.setInfohash(INFO_HASH);
        return state;
    }

    /**
     * 构造一条做种记录。
     *
     * @param id         记录 id
     * @param downloadId 下载任务 id
     * @param localDir   本服务侧目录
     * @return 做种记录
     */
    private static DownloadSeedTask seedRecord(Long id, Long downloadId, String localDir) {
        DownloadSeedTask seed = new DownloadSeedTask();
        seed.setId(id);
        seed.setDownloadId(downloadId);
        seed.setContentGid("gid-content");
        seed.setAria2Dir("/downloads/task");
        seed.setLocalDir(localDir);
        seed.setStatus(SeedTaskStatus.SEEDING);
        return seed;
    }

    /**
     * 构造只设置做种保留时长的 aria2 配置。
     *
     * @param seedTimeMinutes 做种保留时长（分钟）
     * @return aria2 配置
     */
    private static Aria2Property aria2Property(int seedTimeMinutes) {
        Aria2Property property = new Aria2Property();
        property.setSeedTimeMinutes(seedTimeMinutes);
        return property;
    }
}
