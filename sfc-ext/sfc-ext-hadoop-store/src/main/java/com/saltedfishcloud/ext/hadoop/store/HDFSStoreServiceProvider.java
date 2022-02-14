package com.saltedfishcloud.ext.hadoop.store;

import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class HDFSStoreServiceProvider implements StoreServiceProvider, InitializingBean {
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
