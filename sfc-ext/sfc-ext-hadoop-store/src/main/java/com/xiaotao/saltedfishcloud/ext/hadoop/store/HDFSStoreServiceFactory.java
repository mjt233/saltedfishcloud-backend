package com.xiaotao.saltedfishcloud.ext.hadoop.store;

import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
public class HDFSStoreServiceFactory implements StoreServiceFactory, InitializingBean {
    @Autowired
    private HDFSStoreService HDFSStoreService;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("[HDFS]HDFS存储服务已初始化");
    }

    @Override
    public StoreService getService() {
        return HDFSStoreService;
    }
}
