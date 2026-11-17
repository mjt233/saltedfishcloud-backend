package com.sfc.download.engine.p2p;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link P2pTaskState} 的 JSON 往返与容错测试。
 */
class P2pTaskStateTest {

    /**
     * JSON 往返：所有关键字段（阶段、目录、gid、做种、上传）都能正确序列化与反序列化。
     */
    @Test
    void shouldRoundTripAllFields() {
        P2pTaskState state = P2pTaskState.newPrepared(100L, Path.of("/tmp/task-100"),
                "/downloads/offline-download/task-100");
        state.setPhase(P2pTaskState.Phase.DOWNLOADING);
        state.replaceGids(List.of("meta-gid", "content-gid"));
        state.replaceContentGids(List.of("content-gid"));
        state.setInfohash("08ada5a7a6183aae1e09d831df6748d566095a10");
        state.setResumeCount(3);
        state.setSeeding(true);
        state.setSeedTaskId(998877L);
        state.setSeedEndAt(1750604800000L);
        state.setUploadStartedAt(1750000000123L);
        state.setUploaded(true);
        state.setSavedName("movie.mp4");
        state.setSavedBytes(1024L);

        String json = state.toJson();
        Optional<P2pTaskState> parsed = P2pTaskState.fromJson(json);

        assertTrue(parsed.isPresent(), "合法检查点应能反序列化");
        P2pTaskState actual = parsed.get();
        assertEquals(P2pTaskState.CURRENT_VERSION, actual.getVersion());
        assertEquals(100L, actual.getDownloadId());
        assertEquals(P2pTaskState.Phase.DOWNLOADING, actual.getPhase());
        assertEquals("/downloads/offline-download/task-100", actual.getAria2Dir());
        assertEquals(List.of("meta-gid", "content-gid"), actual.getGids());
        assertEquals(List.of("content-gid"), actual.getContentGids());
        assertEquals("08ada5a7a6183aae1e09d831df6748d566095a10", actual.getInfohash());
        assertEquals(3, actual.getResumeCount());
        assertTrue(actual.isSeeding());
        assertEquals(998877L, actual.getSeedTaskId());
        assertEquals(1750604800000L, actual.getSeedEndAt());
        assertNotNull(actual.seedEndAtDate());
        assertEquals(1750000000123L, actual.getUploadStartedAt());
        assertTrue(actual.isUploaded());
        assertEquals("movie.mp4", actual.getSavedName());
        assertEquals(1024L, actual.getSavedBytes());
        assertNotNull(actual.localDirPath());
    }

    /**
     * 未知版本：按「无检查点」处理（返回空）而不是抛异常。
     */
    @Test
    void shouldIgnoreUnknownVersion() {
        P2pTaskState state = P2pTaskState.newPrepared(1L, Path.of("/tmp/x"), "/downloads/x");
        String json = state.toJson().replaceFirst("\"version\":1", "\"version\":99");

        assertTrue(P2pTaskState.fromJson(json).isEmpty());
    }

    /**
     * 非法 JSON：按「无检查点」处理。
     */
    @Test
    void shouldIgnoreInvalidJson() {
        assertTrue(P2pTaskState.fromJson("").isEmpty());
        assertTrue(P2pTaskState.fromJson(null).isEmpty());
        assertTrue(P2pTaskState.fromJson("{not-json").isEmpty());
    }

    /**
     * 缺少阶段字段：按「无检查点」处理（无法决定接管分支）。
     */
    @Test
    void shouldIgnoreMissingPhase() {
        assertTrue(P2pTaskState.fromJson("{\"version\":1,\"downloadId\":\"1\"}").isEmpty());
    }

    /**
     * 阶段判定与目录解析的便捷方法。
     */
    @Test
    void shouldResolvePhaseAndDirectory() {
        P2pTaskState state = P2pTaskState.newPrepared(9L, Path.of("/tmp/dir-9"), "/downloads/dir-9");

        assertTrue(state.isPhase(P2pTaskState.Phase.PREPARED));
        assertFalse(state.isPhase(P2pTaskState.Phase.DOWNLOADING));
        assertTrue(state.belongsTo(9L));
        assertFalse(state.belongsTo(10L));
        assertNotNull(state.localDirPath());
        assertNotNull(state.aria2DirPath());
        assertNull(state.seedEndAtDate());

        state.setPhase(P2pTaskState.Phase.SUBMITTED);
        assertTrue(state.getPhase().isDownloading());
        state.setPhase(P2pTaskState.Phase.UPLOADING);
        assertFalse(state.getPhase().isDownloading());

        state.setLocalDir(null);
        state.setAria2Dir("   ");
        assertNull(state.localDirPath());
        assertNull(state.aria2DirPath());
    }

    /**
     * gid 集合替换：去重、去空白并保持顺序。
     */
    @Test
    void shouldReplaceGidsWithDeduplication() {
        P2pTaskState state = new P2pTaskState();
        state.replaceGids(java.util.Arrays.asList("a", "a", " b ", "", null));
        state.replaceContentGids(List.of("c"));

        assertEquals(List.of("a", "b"), state.getGids());
        assertEquals(List.of("c"), state.getContentGids());

        state.replaceGids(null);
        assertTrue(state.getGids().isEmpty());
    }
}
