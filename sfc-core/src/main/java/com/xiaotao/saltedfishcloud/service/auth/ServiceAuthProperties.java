package com.xiaotao.saltedfishcloud.service.auth;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * 委托令牌机制配置属性。
 * <p>
 * 绑定配置前缀 {@code sys.service.auth}，负责：<b>私钥解析</b>（单行 base64(DER) 或多行 PEM）、
 * <b>格式校验</b>（必须为 RSA 且位数不少于 2048）、<b>公钥派生</b>与 <b>kid 计算</b>，
 * 以及<b>按 profile 判定的启动期校验</b>。
 * </p>
 *
 * <h3>启动校验矩阵</h3>
 * <table border="1">
 *     <caption>配置组合与行为</caption>
 *     <tr><th>情形</th><th>行为</th></tr>
 *     <tr><td>{@code enabled=false}</td><td>不解析密钥、不告警。功能未启用时缺密钥是预期状态</td></tr>
 *     <tr><td>{@code enabled=true} ＋ 私钥缺失 ＋ profile ∈ {develop, develop-mysql}</td>
 *         <td>启动 <b>WARN</b>；真正用到密钥时（JWKS 请求 / 签发调用）抛明确错误</td></tr>
 *     <tr><td>{@code enabled=true} ＋ 私钥缺失 ＋ 其它 profile（含未知 profile）</td>
 *         <td><b>启动失败</b>；错误信息含 openssl 生成命令</td></tr>
 *     <tr><td>私钥存在但格式非法（非 RSA / 位数 &lt; 2048 / 解码失败）</td>
 *         <td><b>任何 profile 都启动失败</b>——"配错了"一律失败，"没配"才分 profile</td></tr>
 * </table>
 *
 * <h3>kid 为什么不可配置</h3>
 * <p>
 * {@code kid} 取<b>公钥的 SHA-256 指纹</b>（base64url），不提供配置项。若开放配置，
 * "换密钥却忘了改 kid"会让微服务认为"kid 未变、缓存仍有效"而不回源重取，
 * 直接导致验签失败。指纹随密钥自动变化，单密钥轮换路径（未知 kid ⇒ 回源一次）才成立。
 * </p>
 *
 * <h3>安全约束</h3>
 * <p>
 * 私钥<b>不得提交进仓库</b>：生产环境只通过环境变量 {@code SERVICE_AUTH_SIGNING_KEY} 注入，
 * 配置文件中只保留空占位符。集群部署时<b>所有 core 节点必须配同一把密钥</b>，
 * 否则负载均衡之后"A 节点签发、B 节点出 JWKS"会验签失败。
 * </p>
 *
 * @see DelegationTokenIssuer
 * @see DelegationTokenVerifier
 */
@Slf4j
@Getter
@Setter
@ConfigurationProperties(prefix = "sys.service.auth")
public class ServiceAuthProperties {

    /**
     * 被视为"开发模式"的 profile 白名单。
     * <p>
     * 刻意使用<b>白名单而非"排除 product"</b>：只有这两个 profile 允许在缺私钥时降级为 WARN，
     * 其余一切（包括 profile 拼错的未知值）一律 fail-fast，偏向严格。
     * </p>
     */
    private static final Set<String> DEVELOPMENT_PROFILES = Set.of("develop", "develop-mysql");

    /** RSA 密钥最小位数要求 */
    private static final int MIN_RSA_KEY_BITS = 2048;


    /**
     * 生成自持 RSA 私钥的命令（缺密钥时的错误提示内容）。
     */
    private static final String KEY_GENERATE_HINT = """
            请生成一把 PKCS#8 格式的 RSA 私钥并注入到配置项 sys.service.auth.signing-key（或环境变量 SERVICE_AUTH_SIGNING_KEY）：
                  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out sfc-service-auth.pem
                  openssl pkcs8 -topk8 -nocrypt -in sfc-service-auth.pem -outform DER | base64 -w0
            也可以直接运行 com.xiaotao.saltedfishcloud.service.auth.ServiceAuthKeyGenerator 打印可粘贴的单行 base64。""";

    /** 委托令牌机制开关。注意语义：{@code false} 表示"关闭"，不是"放开"——{@code /api/internal/**} 仍注册链并一律 401 */
    private boolean enabled = false;

    /** 签名私钥（PKCS#8）。单行 base64(DER) 或多行 PEM 均可；生产环境必须用环境变量注入 */
    private String signingKey = "";

    /** 控制面（header 载体）令牌 TTL（秒），设计约束为不超过 60 秒 */
    private int tokenTtlSeconds = 60;

    /** 数据面（下载，URL query 载体）令牌 TTL（秒） */
    private int downloadTokenTtlSeconds = 300;

    /**
     * {@code aud} 追加白名单。
     * <p>
     * 默认取 {@code ServiceDiscovery} 的已注册服务名集合；这里用于补充或测试。
     * <b>开发验证场景下通常是必需的</b>：{@code registry-provider=local} 时进程内注册表只含自身服务名，
     * 无法放行面向微服务的令牌。
     * </p>
     */
    private List<String> allowedAudiences = new ArrayList<>();

    /** 解析后的签名私钥；{@code null} 表示密钥不可用。刻意不暴露 getter/setter，统一走 {@link #requirePrivateKey()} */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private PrivateKey privateKey;

    /** 由私钥派生的公钥；用于 JWKS 发布与本地验签。刻意不暴露 getter/setter，统一走 {@link #requirePublicKey()} */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private PublicKey publicKey;

    /** 公钥 SHA-256 指纹（base64url 无填充），同时作为 JWT 头部的 {@code kid} */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String keyId;

    /**
     * 校验配置的合法性并解析密钥。
     * <p>
     * 具体行为见类级 JavaDoc 的"启动校验矩阵"。由配置类在上下文初始化时调用一次
     * （与 {@code OidcConfiguration} 调用 {@code OidcServerProperty.validate()} 的写法一致），
     * 从而把 profile 判定所需的激活 profile 列表作为<b>显式参数</b>传入，
     * 避免本类去实现 {@code EnvironmentAware} 之类的框架接口。
     * </p>
     *
     * @param activeProfiles 当前激活的 profile 列表
     * @throws IllegalStateException 配置不合法时抛出，使应用快速失败
     */
    public void validate(List<String> activeProfiles) {
        validateTtl();

        if (!StringUtils.hasText(signingKey)) {
            if (!enabled) {
                // 功能未启用：缺密钥是预期状态，不产生任何日志噪音
                return;
            }
            if (isDevelopmentProfile(activeProfiles)) {
                log.warn("sys.service.auth.enabled=true 但未配置签名私钥（sys.service.auth.signing-key），"
                        + "委托令牌与 JWKS 端点将不可用；当前为开发模式，允许以该状态启动。\n{}", KEY_GENERATE_HINT);
                return;
            }
            throw new IllegalStateException(
                    "sys.service.auth.enabled=true 但未配置签名私钥（sys.service.auth.signing-key），拒绝启动。\n" + KEY_GENERATE_HINT);
        }

        // "配了就必须对"：格式非法在任何 profile 下都启动失败，否则开发模式会得到一个"能启动但一用就错"的假象
        try {
            this.privateKey = parsePrivateKey(signingKey);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "sys.service.auth.signing-key 格式非法，无法解析为 PKCS#8 的 RSA 私钥，拒绝启动。\n" + KEY_GENERATE_HINT, e);
        }

        int bits = ((RSAPrivateKey) privateKey).getModulus().bitLength();
        if (bits < MIN_RSA_KEY_BITS) {
            throw new IllegalStateException("sys.service.auth.signing-key 的 RSA 密钥位数不足 "
                    + MIN_RSA_KEY_BITS + "（实际 " + bits + "），拒绝启动。");
        }

        try {
            this.publicKey = derivePublicKey((RSAPrivateCrtKey) privateKey);
            this.keyId = fingerprint(publicKey);
        } catch (Exception e) {
            throw new IllegalStateException("由私钥派生公钥失败，sys.service.auth.signing-key 可能不完整（缺少 CRT 参数）。", e);
        }

        log.info("委托令牌签名密钥已加载：kid={} 算法=RS256 位数={} 控制面TTL={}s 数据面TTL={}s",
                keyId, bits, tokenTtlSeconds, downloadTokenTtlSeconds);
    }

    /**
     * 判断给定 profile 列表是否命中"允许缺私钥启动"的开发模式白名单。
     *
     * @param activeProfiles 激活的 profile 列表
     * @return 命中白名单时返回 {@code true}
     */
    private static boolean isDevelopmentProfile(List<String> activeProfiles) {
        return activeProfiles != null && activeProfiles.stream().anyMatch(DEVELOPMENT_PROFILES::contains);
    }

    /**
     * 密钥材料是否齐备（私钥已成功解析、公钥与 kid 已派生）。
     *
     * @return 齐备时返回 {@code true}
     */
    public boolean isKeyAvailable() {
        return privateKey != null && publicKey != null && keyId != null;
    }

    /**
     * 获取签名私钥，密钥不可用时抛出带生成命令的明确错误。
     * <p>
     * 刻意<b>不静默返回 null</b>：配置错误应当显式暴露，而不是让调用方退化成 401。
     * </p>
     *
     * @return 签名私钥
     * @throws IllegalStateException 密钥不可用时抛出
     */
    public PrivateKey requirePrivateKey() {
        if (privateKey == null) {
            throw new IllegalStateException("签名私钥不可用，无法签发委托令牌。\n" + KEY_GENERATE_HINT);
        }
        return privateKey;
    }

    /**
     * 获取验签公钥，密钥不可用时抛出带生成命令的明确错误。
     *
     * @return 验签公钥
     * @throws IllegalStateException 密钥不可用时抛出
     */
    public PublicKey requirePublicKey() {
        if (publicKey == null) {
            throw new IllegalStateException("验签公钥不可用。\n" + KEY_GENERATE_HINT);
        }
        return publicKey;
    }

    /**
     * 获取公钥指纹（JWT 头部的 {@code kid}），密钥不可用时抛出带生成命令的明确错误。
     *
     * @return 公钥 SHA-256 指纹（base64url 无填充）
     * @throws IllegalStateException 密钥不可用时抛出
     */
    public String requireKeyId() {
        if (keyId == null) {
            throw new IllegalStateException("签名密钥指纹不可用。\n" + KEY_GENERATE_HINT);
        }
        return keyId;
    }

    /**
     * 校验两个 TTL 配置项的合法性。
     * <p>
     * 控制面 TTL 被强制约束在 {@code (0, 60]} 秒内：设计上"短到不需要撤销列表"依赖这一上界，
     * 允许配置成更长时效会静默破坏该安全性质，因此直接 fail-fast。
     * </p>
     */
    private void validateTtl() {
        if (tokenTtlSeconds <= 0 || tokenTtlSeconds > 60) {
            throw new IllegalStateException("sys.service.auth.token-ttl-seconds 必须位于 (0, 60] 秒内（实际 "
                    + tokenTtlSeconds + "）：控制面委托令牌的短时效是不需要撤销列表的前提。");
        }
        if (downloadTokenTtlSeconds <= 0) {
            throw new IllegalStateException("sys.service.auth.download-token-ttl-seconds 必须大于 0（实际 "
                    + downloadTokenTtlSeconds + "）。");
        }
    }

    /**
     * 解析 PKCS#8 私钥，同时接受单行 base64(DER) 与多行 PEM 两种书写形式。
     *
     * @param raw 配置项原文
     * @return 解析得到的私钥
     * @throws Exception 解码失败或密钥算法不受支持时抛出
     */
    private static PrivateKey parsePrivateKey(String raw) throws Exception {
        String normalized = raw.trim();
        if (normalized.contains("-----BEGIN")) {
            normalized = normalized
                    .replaceAll("-----BEGIN[^-]*-----", "")
                    .replaceAll("-----END[^-]*-----", "")
                    .replaceAll("\\s", "");
        } else {
            normalized = normalized.replaceAll("\\s", "");
        }
        byte[] der = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /**
     * 由 RSA 私钥的 CRT 参数派生公钥——公钥刻意不单独配置，少一个可能配错的项。
     *
     * @param privateKey RSA 私钥（PKCS#8 形式必然是 {@link RSAPrivateCrtKey}）
     * @return 派生出的公钥
     * @throws Exception 派生失败时抛出
     */
    private static PublicKey derivePublicKey(RSAPrivateCrtKey privateKey) throws Exception {
        RSAPublicKeySpec spec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    /**
     * 计算公钥的 SHA-256 指纹并做 base64url 无填充编码，用作 {@code kid}。
     *
     * @param publicKey 公钥
     * @return 指纹字符串
     * @throws Exception 摘要算法不可用时抛出
     */
    private static String fingerprint(PublicKey publicKey) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
