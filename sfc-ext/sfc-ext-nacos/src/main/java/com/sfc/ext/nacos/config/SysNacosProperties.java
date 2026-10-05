package com.sfc.ext.nacos.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nacos provider 专属配置（{@code sys.service.nacos}）。
 * <p>
 * 按「契约层只放抽象概念，实现相关配置留在实现方」的分工，本类定义在插件内而非 {@code sfc-api}：
 * 不安装本插件时这些配置项无意义。启用本插件的前提是 {@code sys.service.registry-provider: nacos}。
 */
@Data
@ConfigurationProperties(prefix = "sys.service.nacos")
public class SysNacosProperties {

    /**
     * 服务端地址，格式 {@code ip:port}；多节点用逗号分隔，如 {@code 127.0.0.1:8848,127.0.0.2:8848}
     */
    private String serverAddr = "127.0.0.1:8848";

    /**
     * 命名空间 ID。
     * <p>
     * public（保留）命名空间的 ID 是<b>空串</b>，故默认留空；填 {@code public} 在部分版本等价于默认命名空间、
     * 在部分版本会指向一个并不存在的命名空间，因此不采用该写法。
     */
    private String namespace = "";

    /**
     * 鉴权用户名，服务端未开启鉴权（{@code nacos.core.auth.enabled=false}）时留空
     */
    private String username = "";

    /**
     * 鉴权密码，服务端未开启鉴权时留空
     */
    private String password = "";

    /**
     * 集群名（Nacos 的 clusterName），用于同一服务内实例的再分组，默认 {@code DEFAULT}
     */
    private String clusterName = "DEFAULT";
}
