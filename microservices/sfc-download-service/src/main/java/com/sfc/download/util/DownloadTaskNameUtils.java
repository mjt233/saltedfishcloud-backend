package com.sfc.download.util;

import com.sfc.common.utils.SfcStringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 下载任务展示名工具。
 *
 * <p>种子 / 磁力链这类 P2P 下载没有单一的文件名：单文件种子只有一个文件名，多文件种子则是一组文件。
 * 列表展示时以「下载文件名摘要」作为任务名称：</p>
 * <ul>
 *   <li><b>一个文件</b>：直接使用该文件名，例如 {@code a.mp4}；</li>
 *   <li><b>两到三个文件</b>：完整拼接，以 {@code 、} 分隔，例如 {@code a.mp4、b.mp3、c.jpg}；</li>
 *   <li><b>超过三个文件</b>：只取前三个文件名，拼接后追加 {@code 等...}，
 *       例如 {@code a.mp4、b.mp3、c.jpg等...}。</li>
 * </ul>
 *
 * <p><b>文件名的口径：</b>入参可以是文件名，也可以是带目录的路径（例如 aria2 返回的绝对路径、
 * 产物相对路径 {@code a/b/c.mp4}），统一只取最后一段作为文件名；空白名称会被忽略，完全相同的名称
 * 只保留首次出现，最终名称按 {@link #MAX_NAME_LENGTH} 截断（与 {@code download_task.name} 列长度一致）。</p>
 *
 * <p>本类只负责「拼装展示文本」，不访问数据库、不感知下载类型；是否使用该名称由调用方决定。</p>
 */
public final class DownloadTaskNameUtils {

    /**
     * 多文件时最多展示的文件名数量。
     */
    private static final int MAX_DISPLAY_FILE_COUNT = 3;

    /**
     * 多个文件名之间的分隔符。
     */
    private static final String NAME_SEPARATOR = "、";

    /**
     * 文件数超过 {@link #MAX_DISPLAY_FILE_COUNT} 时追加的后缀。
     */
    private static final String NAME_SUFFIX = "等...";

    /**
     * 展示名称的最大长度，与 {@code download_task.name} 列长度保持一致。
     */
    private static final int MAX_NAME_LENGTH = 1024;

    /**
     * 路径分隔符（同时兼容 Windows 与网盘路径）。
     */
    private static final char PATH_SEPARATOR = '/';

    /**
     * Windows 路径分隔符，仅用于取文件名。
     */
    private static final char WINDOWS_PATH_SEPARATOR = '\\';

    /**
     * 工具类，禁止实例化。
     */
    private DownloadTaskNameUtils() {
    }

    /**
     * 按文件名列表拼装任务展示名。
     *
     * <p>空白名称会被忽略、重复名称只保留首次出现；入参中的路径只取最后一段文件名。
     * 列表为空或全部为空白时返回 {@code null}（调用方不应覆盖已有名称）。</p>
     *
     * @param fileNames 下载到的文件名或路径列表，允许为 {@code null} 或空
     * @return 任务展示名；没有有效文件名时返回 {@code null}
     */
    public static String buildDisplayName(List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>(fileNames.size());
        Set<String> seen = new LinkedHashSet<>();
        for (String rawName : fileNames) {
            String fileName = extractFileName(rawName);
            if (fileName == null || !seen.add(fileName)) {
                continue;
            }
            normalized.add(fileName);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() == 1) {
            return truncate(normalized.getFirst());
        }

        int displayCount = Math.min(normalized.size(), MAX_DISPLAY_FILE_COUNT);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < displayCount; index++) {
            if (index > 0) {
                builder.append(NAME_SEPARATOR);
            }
            builder.append(normalized.get(index));
        }
        if (normalized.size() > MAX_DISPLAY_FILE_COUNT) {
            builder.append(NAME_SUFFIX);
        }
        return truncate(builder.toString());
    }

    /**
     * 从文件名或路径中取出最后一段文件名。
     *
     * @param rawName 原始文件名或路径，允许为 {@code null} 或空白
     * @return 去除首尾空白后的文件名；入参无有效内容时返回 {@code null}
     */
    private static String extractFileName(String rawName) {
        if (!SfcStringUtils.hasText(rawName)) {
            return null;
        }
        String trimmed = rawName.trim();
        int separator = Math.max(trimmed.lastIndexOf(PATH_SEPARATOR),
                trimmed.lastIndexOf(WINDOWS_PATH_SEPARATOR));
        String fileName = separator >= 0 ? trimmed.substring(separator + 1).trim() : trimmed;
        return fileName.isEmpty() ? null : fileName;
    }

    /**
     * 截断超长名称，且不把代理对（surrogate pair）从中间切开。
     *
     * @param name 原始名称，永不为 {@code null}
     * @return 不超过 {@link #MAX_NAME_LENGTH} 个字符的名称
     */
    private static String truncate(String name) {
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        int end = MAX_NAME_LENGTH;
        if (Character.isHighSurrogate(name.charAt(end - 1))) {
            end--;
        }
        return name.substring(0, end);
    }
}
