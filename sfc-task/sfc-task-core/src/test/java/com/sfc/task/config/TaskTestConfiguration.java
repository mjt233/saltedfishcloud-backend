package com.sfc.task.config;

import com.xiaotao.saltedfishcloud.rpc.RPCManager;
import com.xiaotao.saltedfishcloud.rpc.RedisRPCManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
public class TaskTestConfiguration {
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Bean
    @ConditionalOnMissingBean(RPCManager.class)
    public RPCManager rpcManager() {
        return new RedisRPCManager(redisConnectionFactory);
    }
}
