package com.xiaotao.saltedfishcloud.service.hello;

/**
 * 为系统提供特性标记
 */
public interface FeatureProvider {
    /**
     * 向系统注册特性
     * @param helloService  系统提供的特性注册入口
     */
    void registerFeature(HelloService helloService);
}
