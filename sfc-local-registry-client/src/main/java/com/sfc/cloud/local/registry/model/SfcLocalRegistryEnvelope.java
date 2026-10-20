package com.sfc.cloud.local.registry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册中心 API 响应信封，字段与宿主 {@code SfcJsonResult} 对齐。
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SfcLocalRegistryEnvelope<T> {
    /** 响应码，200 表示成功 */
    private int code = 200;

    /** 细分业务码 */
    private int businessCode = 200;

    /** 提示消息 */
    private String msg;

    /** 业务数据 */
    private T data;
}
