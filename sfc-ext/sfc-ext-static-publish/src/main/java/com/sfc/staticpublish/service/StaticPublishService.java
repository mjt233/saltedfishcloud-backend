package com.sfc.staticpublish.service;

import org.apache.catalina.LifecycleException;

import java.io.IOException;

public interface StaticPublishService {
    /**
     * 启动独立站点服务
     */
    void start() throws LifecycleException, IOException;

    /**
     * 停止站点服务
     */
    void stop() throws LifecycleException;

    /**
     * 服务是否运行中
     */
    boolean isRunning();
}
