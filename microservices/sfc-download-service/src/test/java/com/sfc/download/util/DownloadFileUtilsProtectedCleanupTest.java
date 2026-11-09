package com.sfc.download.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DownloadFileUtils} 陈旧清理的「做种保护目录」测试（fail-safe 语义）。
 */
class DownloadFileUtilsProtectedCleanupTest {

    /**
     * 测试用临时根目录。
     */
    @TempDir
    Path tempRoot;

    /**
     * 每个用例结束后恢复默认探测器，避免静态状态污染其它测试。
     */
    @AfterEach
    void resetProbe() {
        DownloadFileUtils.setProtectedDirectoryProbe(null);
    }

    /**
     * 受保护目录保留、未受保护目录删除。
     *
     * @throws IOException 准备目录失败
     */
    @Test
    void shouldSkipProtectedDirectoryAndDeleteOthers() throws IOException {
        Path protectedDir = createStaleDir("protected");
        Path normalDir = createStaleDir("normal");
        DownloadFileUtils.setProtectedDirectoryProbe(dir -> dir.equals(protectedDir));

        DownloadFileUtils.cleanupStaleTempDirs(tempRoot, Duration.ofDays(7));

        assertTrue(Files.isDirectory(protectedDir), "做种保护目录必须保留");
        assertFalse(Files.exists(normalDir), "普通陈旧目录应被删除");
    }

    /**
     * 保护查询失败时跳过整轮清理（一个都不能删）。
     *
     * @throws IOException 准备目录失败
     */
    @Test
    void shouldSkipRoundWhenProbeFails() throws IOException {
        Path protectedDir = createStaleDir("protected");
        Path normalDir = createStaleDir("normal");
        DownloadFileUtils.setProtectedDirectoryProbe(dir -> {
            throw new IllegalStateException("数据库不可用");
        });

        DownloadFileUtils.cleanupStaleTempDirs(tempRoot, Duration.ofDays(7));

        assertTrue(Files.isDirectory(protectedDir), "查询失败时保护目录必须保留");
        assertTrue(Files.isDirectory(normalDir), "查询失败时应跳过整轮清理，普通目录也不能删");
    }

    /**
     * 未设置探测器时行为与改造前一致（陈旧目录全部删除）。
     *
     * @throws IOException 准备目录失败
     */
    @Test
    void shouldDeleteAllStaleDirsWithoutProbe() throws IOException {
        Path dirA = createStaleDir("a");
        Path dirB = createStaleDir("b");

        DownloadFileUtils.cleanupStaleTempDirs(tempRoot, Duration.ofDays(7));

        assertFalse(Files.exists(dirA));
        assertFalse(Files.exists(dirB));
    }

    /**
     * 非陈旧目录不应被清理。
     *
     * @throws IOException 准备目录失败
     */
    @Test
    void shouldKeepFreshDirectories() throws IOException {
        Path fresh = Files.createDirectory(tempRoot.resolve("fresh"));
        Files.writeString(fresh.resolve("data.txt"), "x");
        AtomicBoolean probed = new AtomicBoolean(false);
        DownloadFileUtils.setProtectedDirectoryProbe(dir -> {
            probed.set(true);
            return false;
        });

        DownloadFileUtils.cleanupStaleTempDirs(tempRoot, Duration.ofDays(7));

        assertTrue(Files.isDirectory(fresh));
        assertFalse(probed.get(), "非陈旧目录不应触发保护查询");
    }

    /**
     * 创建最后修改时间为 30 天前的目录，使其命中 7 天陈旧清理。
     *
     * @param name 目录名
     * @return 已创建并标记为陈旧的目录
     * @throws IOException 创建失败
     */
    private Path createStaleDir(String name) throws IOException {
        Path dir = Files.createDirectory(tempRoot.resolve(name));
        Files.writeString(dir.resolve("data.txt"), "x");
        FileTime old = FileTime.from(Instant.now().minus(Duration.ofDays(30)));
        Files.setLastModifiedTime(dir, old);
        Files.setLastModifiedTime(dir.resolve("data.txt"), old);
        return dir;
    }
}
