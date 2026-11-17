package com.sfc.download.seed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link SeedHashUtils} 单元测试：磁力链 infohash 解析（hex / base32 / URL 编码）、归一化与种子内容 SHA-256。
 */
class SeedHashUtilsTest {

    /**
     * 测试用 infohash（40 位小写 hex）。
     */
    private static final String INFO_HASH_HEX = "08ada5a7a6183aae1e09d831df6748d566095a10";

    /**
     * 与 {@link #INFO_HASH_HEX} 等价的 32 位 Base32 文本。
     */
    private static final String INFO_HASH_BASE32 = "BCW2LJ5GDA5K4HQJ3AY56Z2I2VTASWQQ";

    /**
     * 测试用种子内容。
     */
    private static final byte[] TORRENT_CONTENT = "hello torrent".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    /**
     * 测试用种子内容的 Base64。
     */
    private static final String TORRENT_BASE64 = "aGVsbG8gdG9ycmVudA==";

    /**
     * 测试用种子内容的 SHA-256。
     */
    private static final String TORRENT_SHA256 = "ecb9cc0a989397c1b304da9407a12ed0fc01530a64b0d14f1001d06326c91534";

    @Test
    @DisplayName("磁力链 xt=urn:btih:<40位hex> 解析为小写 hex")
    void parseMagnetInfoHashWithHex() {
        String magnet = "magnet:?xt=urn:btih:" + INFO_HASH_HEX.toUpperCase() + "&dn=test&tr=udp://tracker:80";
        assertEquals(INFO_HASH_HEX, SeedHashUtils.parseMagnetInfoHash(magnet));
    }

    @Test
    @DisplayName("磁力链 xt=urn:btih:<32位base32> 转换为小写 hex")
    void parseMagnetInfoHashWithBase32() {
        String magnet = "magnet:?xt=urn:btih:" + INFO_HASH_BASE32 + "&dn=test";
        assertEquals(INFO_HASH_HEX, SeedHashUtils.parseMagnetInfoHash(magnet));
    }

    @Test
    @DisplayName("URL 编码的 xt 参数也能解析")
    void parseMagnetInfoHashWithEncodedXt() {
        String magnet = "magnet:?xt=urn%3Abtih%3A" + INFO_HASH_HEX + "&dn=test";
        assertEquals(INFO_HASH_HEX, SeedHashUtils.parseMagnetInfoHash(magnet));
    }

    @Test
    @DisplayName("多个 xt 参数时取第一个可解析的 BT infohash")
    void parseMagnetInfoHashWithMultipleXt() {
        String magnet = "magnet:?xt=urn:sha1:ABCDEF&xt=urn:btih:" + INFO_HASH_HEX;
        assertEquals(INFO_HASH_HEX, SeedHashUtils.parseMagnetInfoHash(magnet));
    }

    @Test
    @DisplayName("非磁力链 / 缺少 xt / infohash 长度非法时返回 null")
    void parseMagnetInfoHashReturnsNullForInvalidInput() {
        assertNull(SeedHashUtils.parseMagnetInfoHash(null));
        assertNull(SeedHashUtils.parseMagnetInfoHash("   "));
        assertNull(SeedHashUtils.parseMagnetInfoHash("https://example.com/a.torrent"));
        assertNull(SeedHashUtils.parseMagnetInfoHash("magnet:?dn=only-name"));
        assertNull(SeedHashUtils.parseMagnetInfoHash("magnet:?xt=urn:btih:1234"));
        assertNull(SeedHashUtils.parseMagnetInfoHash("magnet:?xt=urn:btih:"
                + "0123456789abcdef0123456789abcdef0123456g"));
    }

    @Test
    @DisplayName("infohash 归一化：hex 大小写与 base32 都转为小写 hex")
    void normalizeInfoHash() {
        assertEquals(INFO_HASH_HEX, SeedHashUtils.normalizeInfoHash(INFO_HASH_HEX.toUpperCase()));
        assertEquals(INFO_HASH_HEX, SeedHashUtils.normalizeInfoHash("  " + INFO_HASH_HEX + " "));
        assertEquals(INFO_HASH_HEX, SeedHashUtils.normalizeInfoHash(INFO_HASH_BASE32));
        assertEquals(INFO_HASH_HEX, SeedHashUtils.normalizeInfoHash(INFO_HASH_BASE32.toLowerCase()));
        assertNull(SeedHashUtils.normalizeInfoHash(null));
        assertNull(SeedHashUtils.normalizeInfoHash(""));
        // 长度正确但含非 hex 字符
        assertNull(SeedHashUtils.normalizeInfoHash("z123456789abcdef0123456789abcdef0123456"));
        // 长度正确但含非 base32 字符（0/1/8/9 不在 RFC 4648 字母表中）
        assertNull(SeedHashUtils.normalizeInfoHash("01010101010101010101010101010101"));
    }

    @Test
    @DisplayName("种子内容 SHA-256：合法 Base64 返回小写 hex")
    void computeTorrentSha256() {
        assertEquals(TORRENT_SHA256, SeedHashUtils.computeTorrentSha256(TORRENT_BASE64));
        assertEquals(TORRENT_SHA256, SeedHashUtils.computeSha256(TORRENT_CONTENT));
    }

    @Test
    @DisplayName("种子内容 SHA-256：空白 / 非法 Base64 返回 null 而不是抛异常")
    void computeTorrentSha256ReturnsNullForInvalidInput() {
        assertNull(SeedHashUtils.computeTorrentSha256(null));
        assertNull(SeedHashUtils.computeTorrentSha256("   "));
        assertNull(SeedHashUtils.computeTorrentSha256("这不是base64!!!"));
        assertNull(SeedHashUtils.computeTorrentSha256(""));
        assertNull(SeedHashUtils.computeSha256(null));
        assertNull(SeedHashUtils.computeSha256(new byte[0]));
    }

    @Test
    @DisplayName("字节数组转小写 hex")
    void toHex() {
        byte[] bytes = Base64.getDecoder().decode(TORRENT_BASE64);
        assertEquals("68656c6c6f20746f7272656e74", SeedHashUtils.toHex(bytes));
        assertNull(SeedHashUtils.toHex(null));
    }

    @Test
    @DisplayName("base32 字母表合法性：合法 32 位字母表可解析，含 0/1/8/9 的文本被拒绝")
    void base32AlphabetValidation() {
        assertNotNull(SeedHashUtils.normalizeInfoHash("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"));
        assertNull(SeedHashUtils.normalizeInfoHash("ABCDEFGHIJKLMNOPQRSTUVWXYZ234568"));
    }
}
