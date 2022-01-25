package com.xiaotao.saltedfishcloud.ext.store;

import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

public class HadoopStoreServiceFactory implements StoreServiceFactory, InitializingBean {
    @Autowired
    private HadoopStoreService hadoopStoreService;

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("Hadoop 存储服务已启动");
    }

    @Override
    public StoreService getService() {
        return hadoopStoreService;
    }
}
