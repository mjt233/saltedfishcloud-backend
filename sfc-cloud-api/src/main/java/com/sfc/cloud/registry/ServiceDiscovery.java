package com.sfc.cloud.registry;

import com.sfc.cloud.model.ServiceInstance;

import java.util.List;

/**
 * 服务发现 SPI：按服务名查询注册中心中的实例。
 * <p>
 * 查询接口只接收服务名，namespace / group 由 provider 自身的配置决定。这意味着跨服务可见性
 * 依赖"所有服务注册到同一 namespace / group"的部署约定——配置不一致时会出现"注册成功但互相看不见"。
 */
public interface ServiceDiscovery {

    /**
     * 按服务名列出健康实例。
     *
     * @param serviceName 服务名
     * @return 健康实例列表，无匹配时返回空列表
     */
    List<ServiceInstance> listInstances(String serviceName);

    /**
     * 列出所有已知服务名。
     *
     * @return 服务名列表，无匹配时返回空列表
     */
    List<String> listServiceNames();

    /**
     * 该 provider 当前是否可用。
     *
     * @return 可用时返回 true
     */
    boolean isAvailable();
}
