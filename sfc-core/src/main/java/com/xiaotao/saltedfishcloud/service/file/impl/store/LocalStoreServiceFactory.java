package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.File;

@Slf4j
public class LocalStoreServiceFactory implements StoreServiceFactory, InitializingBean {
    @Autowired
    @Qualifier("RAWStoreService")
    private RAWStoreService rawLocalStoreService;

    @Autowired
    @Qualifier("HardLinkStoreService")
    private HardLinkStoreService hardLinkLocalStoreService;

    @Autowired
    private LocalStoreConfig localStoreConfig;

    @Override
    public StoreService getService() {
        if (LocalStoreConfig.STORE_TYPE == StoreType.RAW) {
            return rawLocalStoreService;
        } else if (LocalStoreConfig.STORE_TYPE == StoreType.UNIQUE) {
            return hardLinkLocalStoreService;
        } else {
            throw new UnsupportedOperationException("不支持的存储类型：" + LocalStoreConfig.STORE_TYPE);
        }
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        File[] files = {
                new File(LocalStoreConfig.PUBLIC_ROOT),
                new File(LocalStoreConfig.STORE_ROOT),
                new File(LocalStoreConfig.getRawStoreRoot()),
                new File(LocalStoreConfig.STORE_ROOT + "/user_profile")
        };
        for (File file : files) {
            if (!file.exists()) {
                log.warn("文件夹" + file.getPath() + "不存在，将被创建");
                if (!file.mkdirs()) {
                    log.warn("[目录初始化失败]{}", file);
                }
            } else if (file.isFile()) {
                final IllegalArgumentException e = new IllegalArgumentException(file.toString() + "不是目录");
                e.printStackTrace();
                System.exit(1);
            }
        }
        log.info("[公共网盘路径]" + LocalStoreConfig.PUBLIC_ROOT);
        log.info("[私人网盘根目录]" + LocalStoreConfig.STORE_ROOT);
    }
}
