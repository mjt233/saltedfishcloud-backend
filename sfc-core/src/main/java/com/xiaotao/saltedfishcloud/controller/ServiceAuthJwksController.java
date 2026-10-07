package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.service.auth.ServiceAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 委托令牌的 JWKS 公钥端点。
 * <p>
 * <b>自建、匿名</b>的 {@code GET /api/service-auth/jwks}：微服务通过它取公钥做<b>离线验签</b>，
 * 于是"密钥怎么安全送到校验方"这个问题直接消失——分发的产物是公钥，
 * 它只能验签、不能签发，因此分发它没有安全后果。
 * </p>
 * <p>
 * <b>刻意不复用 {@code /oauth2/jwks}</b>：那样既会与 OIDC 的开关和安全链耦合
 * （{@code sys.oidc.enabled=false} 时微服务鉴权会一起失效），
 * 又要去改 Spring Authorization Server 的安全配置。匿名性由我们自己在
 * {@code SecurityConfig} 的 permitAll 列表中保证。
 * </p>
 *
 * <h3>响应形态</h3>
 * <p>
 * 直接返回 RFC 7517 定义的 <b>JWK Set</b> 文档（{@code {"keys":[...]}}），
 * <b>不套用项目统一的 {@code JsonResultImpl} 包装</b>——这是跨进程线协议的一部分，
 * 需要能被任意标准 JWKS 客户端直接消费。
 * </p>
 *
 * <h3>两种非正常情形</h3>
 * <table border="1">
 *     <caption>与设计文档的启动 / 运行校验矩阵对齐</caption>
 *     <tr><th>情形</th><th>行为</th></tr>
 *     <tr><td>{@code sys.service.auth.enabled=false}</td><td>返回<b>空集合</b> {@code {"keys":[]}}</td></tr>
 *     <tr><td>{@code enabled=true} 但私钥缺失（开发模式下允许启动）</td>
 *         <td>抛出明确错误 ⇒ 500，提示里含生成命令。<b>不静默返回空集合</b>，
 *         否则微服务侧看到的只是"验签失败"，无法定位到"core 侧没配密钥"</td></tr>
 * </table>
 *
 * @see ServiceAuthProperties
 */
@RestController
@RequestMapping("/api/service-auth")
@RequiredArgsConstructor
public class ServiceAuthJwksController {

    private final ServiceAuthProperties properties;

    /**
     * 发布当前签名公钥。
     *
     * @return JWK Set 文档；功能关闭时为空集合
     * @throws IllegalStateException 功能已启用但密钥不可用时抛出（配置错误显式暴露）
     */
    @GetMapping("jwks")
    public JwkSet jwks() {
        if (!properties.isEnabled()) {
            return new JwkSet(List.of());
        }
        // 密钥不可用时 requirePublicKey/requireKeyId 会抛出带生成命令的明确错误
        PublicKey publicKey = properties.requirePublicKey();
        String kid = properties.requireKeyId();
        return new JwkSet(List.of(toJwk(publicKey, kid)));
    }

    /**
     * 把 RSA 公钥转换为 JWK 表示。
     *
     * @param publicKey RSA 公钥
     * @param kid       公钥指纹，即 {@code kid}
     * @return JWK 对象
     */
    private static Jwk toJwk(PublicKey publicKey, String kid) {
        RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
        return new Jwk("RSA", kid, "sig", "RS256",
                encodeUnsigned(rsaPublicKey.getModulus()),
                encodeUnsigned(rsaPublicKey.getPublicExponent()));
    }

    /**
     * 按 JWK 规范做 base64url 无填充编码。
     * <p>
     * {@link BigInteger#toByteArray()} 会为最高位为 1 的数补一个 {@code 0x00} 符号字节，
     * 而 JWK 要求的是<b>无符号</b>大端最简表示，因此必须剥掉这个前导零。
     * </p>
     *
     * @param value 待编码的整数
     * @return base64url（无填充）字符串
     */
    private static String encodeUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * JWK Set 文档。
     *
     * @param keys 公钥数组；功能关闭时为空数组
     */
    public record JwkSet(List<Jwk> keys) {
    }

    /**
     * 单个 RSA 公钥的 JWK 表示（字段名遵循 RFC 7517 / RFC 7518）。
     *
     * @param kty 密钥类型，固定为 {@code RSA}
     * @param kid 密钥 id，为公钥的 SHA-256 指纹（base64url 无填充），随密钥自动变化
     * @param use 用途，固定为 {@code sig}
     * @param alg 算法，固定为 {@code RS256}
     * @param n   RSA 模数（base64url 无填充）
     * @param e   RSA 公开指数（base64url 无填充）
     */
    public record Jwk(String kty, String kid, String use, String alg, String n, String e) {
    }
}
