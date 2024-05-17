package com.sfc.task.config;

import com.sfc.rpc.RPCManager;
import com.sfc.rpc.RedisRPCManager;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
public class TaskTestConfiguration {
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;
    @Autowired
    private ClusterService clusterService;

    @Bean
    @ConditionalOnMissingBean(RPCManager.class)
    public RPCManager rpcManager() {
        return new RedisRPCManager(redisConnectionFactory, clusterService);
    }
}
