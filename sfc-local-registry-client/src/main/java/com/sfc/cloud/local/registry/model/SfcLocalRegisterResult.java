package com.sfc.cloud.local.registry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册成功后的实例信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SfcLocalRegisterResult {
    /** 服务端确认的实例 ID */
    private String instanceId;

    /** 心跳超时时间（秒） */
    private long ttlSeconds;
}
