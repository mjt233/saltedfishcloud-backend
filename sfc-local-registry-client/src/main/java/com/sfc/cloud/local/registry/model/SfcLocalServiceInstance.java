package com.sfc.cloud.local.registry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * local 注册中心返回的实例描述。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SfcLocalServiceInstance {
    /** 服务名 */
    private String serviceId;

    /** 实例 ID */
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
