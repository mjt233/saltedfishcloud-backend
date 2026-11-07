package com.sfc.rpc;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

public class RPCInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(@NotNull ConfigurableApplicationContext applicationContext) {
        RPCResourceBeanPostProcessor rpcResourceBeanPostProcessor = new RPCResourceBeanPostProcessor();
        applicationContext.getBeanFactory().addBeanPostProcessor(rpcResourceBeanPostProcessor);
        applicationContext.addApplicationListener((ApplicationListener<ApplicationReadyEvent>) event -> rpcResourceBeanPostProcessor.clearCache());
    }
}
