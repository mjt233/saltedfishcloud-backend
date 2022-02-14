package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class LocalStoreServiceProvider implements StoreServiceProvider {
    private final StoreService storeService;

    public LocalStoreServiceProvider(StoreService storeService) {
        this.storeService = storeService;
    }

    @Autowired
    private LocalStoreConfig localStoreConfig;

    @Override
    public StoreService getService() {
        if (LocalStoreConfig.STORE_TYPE == StoreType.RAW) {
            return storeService.getRawStoreService();
        } else if (LocalStoreConfig.STORE_TYPE == StoreType.UNIQUE) {
            return storeService.getUniqueStoreService();
        } else {
            throw new UnsupportedOperationException("不支持的存储类型：" + LocalStoreConfig.STORE_TYPE);
        }
    }


//    @Override
//    public void afterPropertiesSet() throws Exception {
//        File[] files = {
//                new File(LocalStoreConfig.PUBLIC_ROOT),
//                new File(LocalStoreConfig.STORE_ROOT),
//                new File(LocalStoreConfig.getRawStoreRoot()),
//                new File(LocalStoreConfig.STORE_ROOT + "/user_profile")
//        };
//        for (File file : files) {
//            if (!file.exists()) {
//                log.warn("文件夹" + file.getPath() + "不存在，将被创建");
//                if (!file.mkdirs()) {
//                    log.warn("[目录初始化失败]{}", file);
//                }
//            } else if (file.isFile()) {
//                final IllegalArgumentException e = new IllegalArgumentException(file.toString() + "不是目录");
//                e.printStackTrace();
//                System.exit(1);
//            }
//        }
//        log.info("[公共网盘路径]" + LocalStoreConfig.PUBLIC_ROOT);
//        log.info("[私人网盘根目录]" + LocalStoreConfig.STORE_ROOT);
//    }
}
