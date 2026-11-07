package com.sfc.task.config;

import com.sfc.rpc.DefaultRPCRegistry;
import com.sfc.rpc.RPCInvoker;
import com.sfc.rpc.RPCRegistry;
import com.sfc.rpc.RPCRegistryStore;
import com.sfc.rpc.RedisRPCInvoker;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 任务模块测试环境下的RPC相关Bean配置。
 */
@Configuration
public class TaskTestConfiguration {
    /**
     * 创建测试环境使用的RPC共享注册存储。
     *
     * @return RPC共享注册存储
     */
    @Bean
    @ConditionalOnMissingBean(RPCRegistryStore.class)
    public RPCRegistryStore rpcRegistryStore() {
        return new RPCRegistryStore();
    }

    /**
     * 创建测试环境使用的RPC调用器。
     *
     * @param redisConnectionFactory Redis连接工厂
     * @param clusterService         集群服务
     * @param rpcRegistryStore       RPC共享注册存储
     * @return RPC调用器
     */
    @Bean
    @ConditionalOnMissingBean(RPCInvoker.class)
    public RPCInvoker rpcInvoker(RedisConnectionFactory redisConnectionFactory,
                                 ClusterService clusterService,
                                 RPCRegistryStore rpcRegistryStore) {
        return new RedisRPCInvoker(redisConnectionFactory, clusterService, rpcRegistryStore);
    }

    /**
     * 创建测试环境使用的RPC注册中心。
     *
     * @param rpcInvoker       RPC调用器
     * @param rpcRegistryStore RPC共享注册存储
     * @return RPC注册中心
     */
    @Bean
    @ConditionalOnMissingBean(RPCRegistry.class)
    public RPCRegistry rpcRegistry(RPCInvoker rpcInvoker, RPCRegistryStore rpcRegistryStore) {
        return new DefaultRPCRegistry(rpcInvoker, rpcRegistryStore);
    }
}
