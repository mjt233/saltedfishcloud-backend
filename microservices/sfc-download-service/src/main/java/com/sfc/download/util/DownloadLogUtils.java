package com.sfc.download.util;

import com.sfc.common.utils.SfcStringUtils;
import lombok.experimental.UtilityClass;

/**
 * 下载日志脱敏工具。
 *
 * <p>下载地址可能内嵌凭证（例如 {@code https://user:token@host/repo.git}），直接写日志会把凭证
 * 泄漏到日志文件。本类提供统一的「地址脱敏」入口，供任务日志、应用日志与参数摘要使用。</p>
 *
 * <p>脱敏规则：只掩码 URI authority 中的 {@code user:password@} 形式（替换为 {@code ***@}）；
 * 仅 {@code user@} 形式（如 SCP 风格 {@code git@host:path}）不含密码，原样返回；无 scheme 的地址
 * （本地路径、SCP 风格）原样返回。</p>
 */
@UtilityClass
public class DownloadLogUtils {

    /**
     * 脱敏后的凭证占位符。
     */
    private static final String MASK = "***@";

    /**
     * 对下载地址做日志脱敏。
     *
     * @param url 原始地址，允许为 {@code null} 或空白
     * @return 脱敏后的地址，永不为 {@code null}
     */
    public static String maskUrl(String url) {
        if (!SfcStringUtils.hasText(url)) {
            return "";
        }
        String trimmed = url.trim();
        int schemeIndex = trimmed.indexOf("://");
        if (schemeIndex < 0) {
            // 无 scheme：SCP 风格或本地路径，不含内嵌密码
            return trimmed;
        }
        int authorityStart = schemeIndex + 3;
        int pathStart = trimmed.indexOf('/', authorityStart);
        if (pathStart < 0) {
            pathStart = trimmed.length();
        }
        int atIndex = trimmed.indexOf('@', authorityStart);
        if (atIndex < 0 || atIndex > pathStart) {
            return trimmed;
        }
        int colonIndex = trimmed.indexOf(':', authorityStart);
        if (colonIndex < 0 || colonIndex > atIndex) {
            // 仅 user@ 形式，没有密码
            return trimmed;
        }
        return trimmed.substring(0, authorityStart) + MASK + trimmed.substring(atIndex + 1);
    }
}
