package com.sfc.cloud.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代理节点信息（宿主与微服务共享模型）。
 *
 * <p>宿主的代理实体 {@code com.xiaotao.saltedfishcloud.model.po.ProxyInfo} 带 JPA 注解且位于
 * 宿主模块，微服务无法直接依赖，因此宿主通过内部接口对外暴露代理节点时统一使用本模型承载数据。</p>
 *
 * <p>字段与宿主代理实体的「可供外部使用」字段一一对应，只保留建立代理连接所必需的信息；
 * 测试 URL、保护标记等仅供宿主内部使用的字段不下发。本类<b>零 JPA 依赖</b>，
 * 可被网关等非持久化场景安全使用。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SfcProxyInfo {

    /** 代理节点 id（宿主代理表主键，用于去重与定位） */
    private Long id;

    /** 代理节点名称（同一所有者下唯一，前端按名称选择代理） */
    private String name;

    /** 代理协议类型，取值为 {@code HTTP} 或 {@code SOCKS}（与宿主代理类型枚举的 {@code name()} 一致） */
    private String type;

    /** 代理服务器地址（主机名或 IP，不含协议前缀与端口） */
    private String address;

    /** 代理服务器端口，取值 1~65535 */
    private Integer port;
}
