package com.sfc.rpc.config;

import com.sfc.rpc.DefaultRPCManager;
import com.sfc.rpc.RPCInvoker;
import com.sfc.rpc.RPCManager;
import com.sfc.rpc.RPCRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.sfc.rpc")
public class RpcAutoConfiguration {

    @Bean
    public RPCManager rpcManager(RPCInvoker invoker, RPCRegistry registry) {
        return new DefaultRPCManager(invoker, registry);
    }
}
