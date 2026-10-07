package com.sfc.cloud.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 委托令牌的公钥提供者：拉取 core 侧 JWKS、按 {@code kid} 缓存、必要时回源。
 * <p>
 * <b>本类只持有公钥</b>——公钥只能验签、不能签发，因此"微服务被攻破 ≠ core 被攻破"
 * 在结构上成立，而不是靠约定。分发公钥也没有安全后果，
 * 于是"密钥怎么安全送到校验方"这个问题直接消失。
 * </p>
 *
 * <h3>缓存与回源策略</h3>
 * <ul>
 *     <li><b>正常路径零网络调用</b>：公钥从本地缓存按 {@code kid} 命中；</li>
 *     <li>缓存过期（{@code jwks-cache-seconds}，默认 300 秒）时回源刷新；</li>
 *     <li><b>未知 {@code kid} 必须回源重取一次再判定失败</b>——这是单密钥方案能否平稳轮换的
 *     <b>唯一支点</b>，已从实现建议升级为契约硬要求与验收项。
 *     轮换路径：替换配置里的密钥 ⇒ 新签发的令牌带新 {@code kid}
 *     ⇒ 微服务发现未知 {@code kid} ⇒ 回源取到新公钥 ⇒ 通过；</li>
 *     <li>刷新失败时<b>保留旧缓存</b>，避免 core 侧一次抖动导致所有请求 401。</li>
 * </ul>
 * <p>
 * 刷新动作是 {@code synchronized} 的，并带一个 1 秒的合并窗口，
 * 防止"用随机 {@code kid} 打请求"构造出对 core JWKS 端点的放大攻击。
 * 该窗口不会影响正常的密钥轮换（真轮换不会在 1 秒内发生两次）。
 * </p>
 */
@Slf4j
public class JwksProvider {

    /** 回源失败后的重试间隔（毫秒）：失败不写满整段缓存时长，避免长时间沿用旧快照 */
    private static final long FAILED_REFRESH_RETRY_MILLIS = 5_000L;

    /**
     * 强制回源的合并窗口（毫秒）。
     * <p>
     * 防止未知 {@code kid} 被用来对 core 的 JWKS 端点做请求放大；
     * 正常的密钥轮换不会在 1 秒内连续发生两次，因此不影响轮换的平稳性。
     * </p>
     */
    private static final long FORCED_REFRESH_COALESCE_MILLIS = 1_000L;

    private final ServiceAuthClientProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 按 {@code kid} 索引的公钥快照 */
    private volatile Map<String, PublicKey> keyCache = Map.of();

    /** 公钥快照的过期时间戳（毫秒） */
    private volatile long keyCacheExpireAt = 0L;

    /** 上一次刷新动作的结束时间戳（毫秒），用于强制回源的合并窗口 */
    private volatile long lastRefreshFinishedAt = 0L;

    /**
     * 构造公钥提供者。
     *
     * @param properties 微服务侧验签配置，提供 JWKS 地址与超时
     */
    public JwksProvider(ServiceAuthClientProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    /**
     * 按 {@code kid} 取验签公钥，未命中时回源重取一次。
     *
     * @param kid 令牌头部的密钥 id
     * @return 对应的公钥
     * @throws InternalTokenException 缺少 {@code kid}，或回源一次后仍无对应公钥时抛出
     */
    public PublicKey resolveKey(String kid) {
        if (!StringUtils.hasText(kid)) {
            throw new InternalTokenException("委托令牌缺少 kid，无法定位验签公钥");
        }

        if (System.currentTimeMillis() >= keyCacheExpireAt) {
            refresh();
        }
        PublicKey key = keyCache.get(kid);
        if (key != null) {
            return key;
        }

        // 未知 kid：回源重取一次（契约硬要求）。合并窗口内的重复请求不会再次回源。
        refreshForced();
        key = keyCache.get(kid);
        if (key == null) {
            throw new InternalTokenException("JWKS 中不存在 kid=" + kid + " 对应的公钥");
        }
        return key;
    }

    /**
     * 按缓存过期时间决定是否回源刷新。
     */
    private void refresh() {
        doRefresh();
    }

    /**
     * 强制回源刷新，但受 1 秒合并窗口约束。
     */
    private void refreshForced() {
        if (System.currentTimeMillis() - lastRefreshFinishedAt < FORCED_REFRESH_COALESCE_MILLIS) {
            return;
        }
        doRefresh();
    }

    /**
     * 执行一次 JWKS 拉取并替换快照。
     * <p>
     * 失败时<b>不覆盖</b>已有快照，只把过期时间缩短到 {@value #FAILED_REFRESH_RETRY_MILLIS} 毫秒后，
     * 让下一次请求有机会重试。
     * </p>
     */
    private synchronized void doRefresh() {
        long now = System.currentTimeMillis();
        try {
            Map<String, PublicKey> fetched = fetchKeys();
            keyCache = fetched;
            keyCacheExpireAt = now + properties.getJwksCacheSeconds() * 1000L;
            log.debug("JWKS 刷新完成：kid 数量={}", fetched.size());
        } catch (Exception e) {
            keyCacheExpireAt = now + FAILED_REFRESH_RETRY_MILLIS;
            log.warn("JWKS 刷新失败，继续沿用已有公钥快照（{} 个）: {}",
                    keyCache.size(), e.getMessage());
        } finally {
            lastRefreshFinishedAt = System.currentTimeMillis();
        }
    }

    /**
     * 拉取并解析 JWKS 文档。
     *
     * @return 按 {@code kid} 索引的公钥映射；core 侧功能关闭时为空映射
     * @throws Exception 网络失败、响应非 200 或文档格式非法时抛出
     */
    private Map<String, PublicKey> fetchKeys() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getJwksUrl()))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("JWKS 端点返回 HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode keys = root == null ? null : root.get("keys");
        if (keys == null || !keys.isArray()) {
            throw new IllegalStateException("JWKS 响应缺少 keys 数组");
        }

        Map<String, PublicKey> result = new LinkedHashMap<>();
        for (JsonNode node : keys) {
            String kty = textOf(node, "kty");
            String kid = textOf(node, "kid");
            String modulus = textOf(node, "n");
            String exponent = textOf(node, "e");
            if (!"RSA".equals(kty) || !StringUtils.hasText(kid)
                    || !StringUtils.hasText(modulus) || !StringUtils.hasText(exponent)) {
                log.warn("忽略 JWKS 中不受支持的条目：kty={} kid={}", kty, kid);
                continue;
            }
            result.put(kid, toPublicKey(modulus, exponent));
        }
        return Map.copyOf(result);
    }

    /**
     * 由 JWK 的 {@code n} / {@code e} 还原 RSA 公钥。
     *
     * @param modulus  模数的 base64url 编码
     * @param exponent 公开指数的 base64url 编码
     * @return RSA 公钥
     * @throws Exception 密钥构造失败时抛出
     */
    private static PublicKey toPublicKey(String modulus, String exponent) throws Exception {
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
    }

    /**
     * 读取 JSON 节点的文本值。
     *
     * @param node  JSON 节点
     * @param field 字段名
     * @return 字段文本；缺失或为空时返回 {@code null}
     */
    private static String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
