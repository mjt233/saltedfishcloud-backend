package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.store.CopyAndMoveHandler;
import com.xiaotao.saltedfishcloud.service.file.store.CopyAndMoveProperty;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 数据迁移相关工具类
 */
@UtilityClass
@Slf4j
public class MigrateUtils {

    /**
     * 在系统的存储服务中移动数据
     * @param src   在存储服务中的源路径
     * @param dest  在存储服务中的目标新路径
     */
    public static void moveDirectory(String src, String dest) throws IOException {
        Storage storageProvider = SpringContextUtils.getContext().getBean(StoreServiceFactory.class).getService().getStorageProvider();
        String root = SpringContextUtils.getContext().getBean(SysProperties.class).getStore().getRoot();
        String srcPath = StringUtils.appendPath(root, src);
        String descPath = StringUtils.appendPath(root, dest);

        log.info("开始迁移数据 {} => {}", srcPath, descPath);
        if (storageProvider.exist(descPath)) {
            if(storageProvider.listFiles(descPath).isEmpty()) {
                storageProvider.delete(descPath);
                storageProvider.move(srcPath, descPath, null);
            } else {
                CopyAndMoveHandler.createByStoreHandler(storageProvider, CopyAndMoveProperty.builder()
                                .isMoveWithRecursion(true)
                                .isCopyWithRecursion(true)
                                .build())
                        .move(srcPath, descPath, true);
            }
        } else {
            storageProvider.move(srcPath, descPath, null);
        }
    }
}
