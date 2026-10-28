package com.sfc.download.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 下载产物描述。
 *
 * <p>一个下载任务可能产生单个文件（HTTP、单文件种子），也可能产生一棵目录树（Git 仓库、
 * 多文件种子）。引擎执行完成后返回本记录的列表，由落盘服务统一写入网盘。</p>
 *
 * <p><b>路径约定：</b>{@link #relativePath()} 始终是相对于任务临时目录（{@link DownloadContext#getTempDir()}）
 * 的路径，使用 {@code '/'} 作为分隔符，且不允许出现 {@code ..} 片段——落盘服务据此在网盘上还原目录结构。</p>
 *
 * @param relativePath 相对任务临时目录的路径，使用 {@code '/'} 分隔
 * @param localPath    本地产物的绝对路径
 * @param size         产物字节数；目录产物为 {@code 0}
 */
public record DownloadArtifact(String relativePath, Path localPath, long size) {

    /**
     * 规范化并校验产物描述。
     *
     * @throws IllegalArgumentException 相对路径为空、包含 {@code ..} 片段、本地路径为空或大小为负数时抛出
     */
    public DownloadArtifact {
        relativePath = normalizeRelativePath(relativePath);
        if (localPath == null) {
            throw new IllegalArgumentException("产物本地路径不能为空");
        }
        if (size < 0) {
            throw new IllegalArgumentException("产物大小不能为负数: " + size);
        }
    }

    /**
     * 以「任务临时目录 + 本地文件」构造产物描述。
     *
     * <p>自动把 {@code file} 归一化为绝对路径后相对 {@code root} 计算 {@link #relativePath()}，
     * 并读取文件实际大小（目录记为 {@code 0}）。</p>
     *
     * @param root 任务临时目录（{@link DownloadContext#getTempDir()}）
     * @param file 本地产物文件或其子目录
     * @return 产物描述
     * @throws IllegalArgumentException 入参为空、产物不在 {@code root} 之下，或产物就是 {@code root} 本身时抛出
     * @throws UncheckedIOException     读取产物大小时 IO 异常
     */
    public static DownloadArtifact of(Path root, Path file) {
        if (root == null) {
            throw new IllegalArgumentException("任务临时目录不能为空");
        }
        if (file == null) {
            throw new IllegalArgumentException("产物本地路径不能为空");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (normalizedFile.equals(normalizedRoot)) {
            throw new IllegalArgumentException("产物不能是任务临时目录本身: " + normalizedRoot);
        }
        if (!normalizedFile.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("产物不在任务临时目录之下: " + normalizedFile);
        }
        String relativePath = normalizedRoot.relativize(normalizedFile).toString().replace('\\', '/');
        long size;
        try {
            size = Files.isDirectory(normalizedFile) ? 0L : Files.size(normalizedFile);
        } catch (IOException e) {
            throw new UncheckedIOException("读取产物大小失败: " + normalizedFile, e);
        }
        return new DownloadArtifact(relativePath, normalizedFile, size);
    }

    /**
     * 规范化相对路径：反斜杠转正斜杠、去掉开头的 {@code /} 与 {@code ./}，并拒绝 {@code ..} 片段。
     *
     * @param relativePath 原始相对路径
     * @return 规范化后的相对路径
     * @throws IllegalArgumentException 路径为空或包含 {@code ..} 片段时抛出
     */
    private static String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("产物相对路径不能为空");
        }
        String normalized = relativePath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("产物相对路径不能为空: " + relativePath);
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("产物相对路径不允许包含 .. 片段: " + relativePath);
            }
        }
        return normalized;
    }
}
