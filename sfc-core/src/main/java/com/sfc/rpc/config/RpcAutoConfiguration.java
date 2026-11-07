package com.sfc.rpc.config;

import com.sfc.rpc.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RPC 模块自动配置类。
 * <p>
 * 使用 {@link Import} 显式注册各组件，避免包扫描带来的类路径遍历开销，提升启动速度。
 * {@link MQRPCInvoker} 与 {@link RedisRPCInvoker} 自带 {@code @ConditionalOnProperty}，
 * 在 {@code @Import} 场景下条件判断依然正常生效。
 */
@Configuration
@Import({
        DefaultRPCRegistry.class,
        MQRPCInvoker.class,
        RedisRPCInvoker.class,
        RPCRegistryStore.class
})
public class RpcAutoConfiguration {

    /**
     * 注册默认 RPC 管理器。
     *
     * @param invoker  RPC 调用器
     * @param registry RPC 注册中心
     * @return {@link DefaultRPCManager} 实例
     */
    @Bean
    public RPCManager rpcManager(RPCInvoker invoker, RPCRegistry registry) {
        return new DefaultRPCManager(invoker, registry);
    }
}
