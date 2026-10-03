package com.sfc.cloud.registry;

import com.sfc.cloud.model.ServiceDefinition;
import com.sfc.cloud.model.ServiceInstance;

import java.io.Closeable;

/**
 * 服务注册 SPI：把"本服务实例在哪"告知注册中心。
 * <p>
 * 职责划分：调用方负责解析自身身份（{@link ServiceDefinition}）与对外地址（{@link ServiceInstance}），
 * provider 只负责注册中心协议本身，无需重复实现地址解析逻辑。
 * <p>
 * 本接口刻意【不提供】续约（renew）方法：续约语义属于 provider 的内部实现细节——自研的 cluster provider
 * 由既有集群心跳定时任务续约，Nacos provider 由客户端内置心跳续约。把它暴露到 SPI 会迫使调用方关心
 * "什么时候该续约"，属错误的分层；SPI 中保留但无人调用的方法是坏设计。
 * <p>
 * 继承自 {@link Closeable} 的 {@link Closeable#close()} 用于释放 provider 持有的资源，
 * 实现类通常在其中调用 {@link #deregister()}。
 */
public interface ServiceRegistry extends Closeable {

    /**
     * 注册当前实例。重复调用应保证幂等。
     *
     * @param definition 自身服务描述
     * @param instance   自身实例信息
     */
    void register(ServiceDefinition definition, ServiceInstance instance);

    /**
     * 注销当前实例。
     */
    void deregister();

    /**
     * 该 provider 当前是否可用（如注册中心连接断开时应返回 false）。
     *
     * @return 可用时返回 true
     */
    boolean isAvailable();
}
