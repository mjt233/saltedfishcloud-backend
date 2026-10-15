package com.sfc.cloud.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 内部接口鉴权配置项，前缀 {@code sfc.internal}。
 *
 * <p>core 与所有微服务共用同一份密钥（环境变量 {@code SFC_INTERNAL_KEY}）；其余项两端各自可调。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sfc.internal")
public class SfcInternalAuthProperties {

    /**
     * 服务间共享密钥（环境变量 {@code SFC_INTERNAL_KEY}）。<br>
     * 用于签发与校验 {@link SfcInternalAuthConstants.Header#INTERNAL_TOKEN} 中的 HS256 JWT，
     * <b>永不在网络上传输</b>。留空表示未配置：服务端拒绝全部内部接口请求（fail-closed），
     * 客户端无法生成凭证；设置时不得少于 {@link SfcInternalAuthConstants#MIN_KEY_LENGTH} 个字符（否则启动失败）。
     */
    private String key = "";

    /**
     * 本服务的名称，写入 JWT 的 {@code iss} 声明（便于服务端审计/白名单）。
     * 留空时取 {@code spring.application.name}，再取 {@link SfcInternalAuthConstants#DEFAULT_SERVICE_NAME}。
     */
    private String serviceName = "";

    /**
     * 签发凭证的有效期，默认 5 分钟。<br>
     * <b>本 SDK 不做防重放</b>，因此这是"凭证泄露后可用窗口"的唯一控制手段：有效期越短，窗口越小。
     */
    private Duration tokenTtl = Duration.ofMinutes(5);

    /** 校验时允许的时钟偏移，默认 30 秒（用于容忍双方机器时钟差异） */
    private Duration clockSkew = Duration.ofSeconds(30);

    /**
     * 是否在 Servlet 应用中启用服务端校验过滤器，默认 {@code true}。<br>
     * 默认开启是为了避免"忘了开配置 = 门开着"；如需让某个服务不承担内部接口提供方角色，显式置为 {@code false}。
     */
    private boolean serverEnabled = true;

    /**
     * 允许的调用方服务名（{@code iss}）白名单，默认空表示<b>不校验</b>。<br>
     * 非空时只接受名单内的服务名——注意它只是"来源声明"，不能替代按服务分发密钥。
     */
    private List<String> allowedIssuers = new ArrayList<>();
}
