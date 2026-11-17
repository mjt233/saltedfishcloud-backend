package com.sfc.download.seed;

import com.sfc.common.utils.SfcStringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 做种保留相关的指纹工具：磁力链 infohash 解析、infohash 归一化与种子内容 SHA-256 计算。
 *
 * <p><b>infohash：</b>BT 协议中的 infohash 是 20 字节（160 bit）摘要，外部文本形式有两种：</p>
 * <ul>
 *   <li>40 位十六进制（最常见，大小写不敏感）；</li>
 *   <li>32 位 Base32（RFC 4648 字母表 {@code A-Z2-7}，大小写不敏感）。</li>
 * </ul>
 * <p>本类统一把它们归一化为<b>小写 hex</b> 字符串再入库/比较，避免同一个种子因大小写或编码形式不同而无法
 * 命中复用。</p>
 *
 * <p><b>容错约定：</b>所有解析/计算失败都返回 {@code null}，不抛异常、不阻断下载任务。契约要求
 * 「磁力解析失败不阻断任务，只是失去复用能力」，因此调用方拿到 {@code null} 时按「无指纹」继续执行即可。</p>
 *
 * <p>本类无状态、线程安全。</p>
 */
public final class SeedHashUtils {

    /**
     * infohash 的十六进制文本长度（20 字节 × 2）。
     */
    private static final int INFO_HASH_HEX_LENGTH = 40;

    /**
     * infohash 的 Base32 文本长度（160 bit ÷ 5 bit）。
     */
    private static final int INFO_HASH_BASE32_LENGTH = 32;

    /**
     * infohash 的字节长度。
     */
    private static final int INFO_HASH_BYTES = 20;

    /**
     * Base32 字母表（RFC 4648，无填充时 infohash 固定 32 字符）。
     */
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * 磁力链前缀。
     */
    private static final String MAGNET_PREFIX = "magnet:?";

    /**
     * 磁力链精确主题前缀（BT infohash）。
     */
    private static final String BTIH_PREFIX = "urn:btih:";

    /**
     * 十六进制格式化器（小写）。
     */
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    /**
     * 工具类禁止实例化。
     */
    private SeedHashUtils() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 从磁力链中解析 infohash。
     *
     * <p>只识别 {@code xt=urn:btih:<hash>} 形式的精确主题（BT 协议），其中 {@code <hash>} 允许是
     * 40 位 hex 或 32 位 Base32。解析路径：按 {@code &} 拆分查询串 → 逐个取 {@code xt} 参数 →
     * URL 解码（兼容 {@code urn%3Abtih%3A...} 这类整体编码的写法）→ 归一化为小写 hex。
     * 同一个磁力链有多个 {@code xt} 参数时取第一个能成功解析为 BT infohash 的。</p>
     *
     * <p>非磁力链、没有 {@code xt}、或 infohash 格式非法时返回 {@code null}（不抛异常）。</p>
     *
     * @param magnetUri 磁力链，允许为 {@code null} 或空白
     * @return 小写 hex 的 infohash；无法解析时为 {@code null}
     */
    public static String parseMagnetInfoHash(String magnetUri) {
        if (!SfcStringUtils.hasText(magnetUri)) {
            return null;
        }
        String value = magnetUri.trim();
        if (value.length() <= MAGNET_PREFIX.length()
                || !value.regionMatches(true, 0, MAGNET_PREFIX, 0, MAGNET_PREFIX.length())) {
            return null;
        }
        String query = value.substring(MAGNET_PREFIX.length());
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = parameter.substring(0, separator);
            if (!"xt".equalsIgnoreCase(name)) {
                continue;
            }
            String xt = decodeUrlComponent(parameter.substring(separator + 1));
            if (!SfcStringUtils.hasText(xt) || !xt.regionMatches(true, 0, BTIH_PREFIX, 0, BTIH_PREFIX.length())) {
                continue;
            }
            String infoHash = normalizeInfoHash(xt.substring(BTIH_PREFIX.length()));
            if (infoHash != null) {
                return infoHash;
            }
        }
        return null;
    }

    /**
     * 把 infohash 的文本形式归一化为小写 hex。
     *
     * @param rawInfoHash 40 位 hex（大小写不敏感）或 32 位 Base32（大小写不敏感）文本，允许为
     *                    {@code null} 或带首尾空白
     * @return 40 位小写 hex；格式非法时返回 {@code null}
     */
    public static String normalizeInfoHash(String rawInfoHash) {
        if (rawInfoHash == null) {
            return null;
        }
        String value = rawInfoHash.trim();
        if (value.length() == INFO_HASH_HEX_LENGTH) {
            return isHex(value) ? value.toLowerCase(Locale.ROOT) : null;
        }
        if (value.length() == INFO_HASH_BASE32_LENGTH) {
            byte[] bytes = decodeBase32(value);
            return bytes == null ? null : HEX_FORMAT.formatHex(bytes);
        }
        return null;
    }

    /**
     * 计算种子内容（Base64 文本）的 SHA-256，用于「同一种子文件」复用匹配。
     *
     * <p>内容为空、Base64 非法或摘要算法不可用时返回 {@code null}（不抛异常，调用方按「无种子指纹」
     * 继续执行）。</p>
     *
     * @param torrentBase64 种子文件内容的 Base64 编码，允许为 {@code null} 或空白
     * @return 64 位小写 hex 的 SHA-256；无法计算时为 {@code null}
     */
    public static String computeTorrentSha256(String torrentBase64) {
        if (!SfcStringUtils.hasText(torrentBase64)) {
            return null;
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(torrentBase64.trim());
        } catch (IllegalArgumentException e) {
            // 种子内容非法时只失去复用能力，不应阻断下载任务
            return null;
        }
        return content.length == 0 ? null : computeSha256(content);
    }

    /**
     * 计算字节内容的 SHA-256 摘要并转为小写 hex。
     *
     * @param content 待计算内容，允许为 {@code null}
     * @return 64 位小写 hex；内容为空或摘要算法不可用时返回 {@code null}
     */
    public static String computeSha256(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        try {
            return HEX_FORMAT.formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            // 所有合规 JRE 都内置 SHA-256，这里只做防御性兜底
            return null;
        }
    }

    /**
     * 把字节数组格式化为小写 hex 字符串。
     *
     * @param bytes 字节数组，允许为 {@code null}
     * @return 小写 hex 字符串；入参为 {@code null} 时返回 {@code null}
     */
    public static String toHex(byte[] bytes) {
        return bytes == null ? null : HEX_FORMAT.formatHex(bytes);
    }

    /**
     * 判断文本是否全部由十六进制字符组成。
     *
     * @param text 待判断文本
     * @return 全部是十六进制字符返回 {@code true}
     */
    private static boolean isHex(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解码 32 位 Base32 文本为 20 字节 infohash。
     *
     * <p>只接受无填充的标准 Base32（32 字符 = 160 bit = 20 字节，bit 数刚好对齐）。
     * 字母表大小写不敏感；含非法字符或解码结果不是 20 字节时返回 {@code null}。</p>
     *
     * @param text 32 位 Base32 文本
     * @return 20 字节摘要；格式非法时返回 {@code null}
     */
    private static byte[] decodeBase32(String text) {
        byte[] result = new byte[INFO_HASH_BYTES];
        int buffer = 0;
        int bitsInBuffer = 0;
        int outputIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            int value = BASE32_ALPHABET.indexOf(Character.toUpperCase(text.charAt(i)));
            if (value < 0) {
                return null;
            }
            buffer = (buffer << 5) | value;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                if (outputIndex >= INFO_HASH_BYTES) {
                    // 32 字符恰好 20 字节，理论不会走到这里；防御性返回 null 而不是抛数组越界
                    return null;
                }
                result[outputIndex++] = (byte) ((buffer >> bitsInBuffer) & 0xFF);
            }
        }
        return bitsInBuffer == 0 && outputIndex == INFO_HASH_BYTES ? result : null;
    }

    /**
     * 对 URL 组件做百分号解码。
     *
     * <p>与 {@code DownloadFileUtils} 的做法一致：先把 {@code "+"} 转义为 {@code "%2B"}，避免
     * {@link URLDecoder} 把 infohash 中偶发的加号解释为空格；解码失败时原样返回。</p>
     *
     * @param raw 原始文本
     * @return 解码后的文本
     */
    private static String decodeUrlComponent(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }
}
