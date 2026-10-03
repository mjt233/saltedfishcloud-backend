package com.sfc.cloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 服务定义：描述"我是谁"。
 * <p>
 * 与 {@link ServiceInstance}（描述"我在哪"）一起构成注册所需的完整信息。
 * {@code namespace} / {@code group} 对应注册中心的命名空间与分组；
 * 由于发现侧只按服务名查询，跨服务可见性依赖"所有服务注册到同一 namespace / group"的部署约定。
 * <p>
 * 注意：{@link NoArgsConstructor} 与 {@link AllArgsConstructor} 是必要的——lombok 的
 * {@link Builder} 会生成全参构造器，从而不再生成默认无参构造器，而反序列化需要无参构造器。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceDefinition {

    /**
     * 服务名，如 sfc-core
     */
    private String serviceName;

    /**
     * 分组，对应注册中心（如 Nacos）的 group
     */
    private String group;

    /**
     * 命名空间，对应注册中心（如 Nacos）的 namespace
     */
    private String namespace;

    /**
     * 扩展元数据
     */
    private Map<String, String> metadata;
}
