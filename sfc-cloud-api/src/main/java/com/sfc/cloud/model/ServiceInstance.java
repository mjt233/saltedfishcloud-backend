package com.sfc.cloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 服务实例：描述一个具体可对外访问的服务节点。
 * <p>
 * 该模型需要跨进程传输（注册到注册中心 / 从注册中心查询返回），因此使用具体 POJO 而非接口，
 * 以避免引入多态序列化（{@code @JsonTypeInfo}）的复杂度。
 * <p>
 * 注意：{@link NoArgsConstructor} 与 {@link AllArgsConstructor} 是必要的——lombok 的
 * {@link Builder} 会生成全参构造器，从而不再生成默认无参构造器，而反序列化需要无参构造器。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceInstance {

    /**
     * 服务名，如 sfc-core
     */
    private String serviceName;

    /**
     * 实例唯一标识
     */
    private String instanceId;

    /**
     * 对外可达的主机名或 IP
     */
    private String host;

    /**
     * HTTP 端口
     */
    private int port;

    /**
     * 是否健康
     */
    private boolean healthy;

    /**
     * 扩展元数据（版本号、协议等自由 KV）
     */
    private Map<String, String> metadata;
}
