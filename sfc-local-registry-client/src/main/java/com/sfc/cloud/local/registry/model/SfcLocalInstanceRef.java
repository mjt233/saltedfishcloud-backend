package com.sfc.cloud.local.registry.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实例引用，用于心跳与注销请求。
 */
@Data
@NoArgsConstructor
public class SfcLocalInstanceRef {
    /** 服务名 */
    private String serviceId;

    /** 实例 ID */
    private String instanceId;
}
