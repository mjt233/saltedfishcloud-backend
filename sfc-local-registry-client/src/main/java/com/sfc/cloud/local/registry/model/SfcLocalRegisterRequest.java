package com.sfc.cloud.local.registry.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 外部实例注册请求。
 */
@Data
@NoArgsConstructor
public class SfcLocalRegisterRequest {
    /** 服务名 */
    private String serviceId;

    /** 实例 ID，留空时由服务端使用 {@code host:port} */
    private String instanceId;

    /** 实例 host */
    private String host;

    /** 实例端口 */
    private int port;

    /** 是否 HTTPS */
    private boolean secure;

    /** 实例元数据 */
    private Map<String, String> metadata;
}
