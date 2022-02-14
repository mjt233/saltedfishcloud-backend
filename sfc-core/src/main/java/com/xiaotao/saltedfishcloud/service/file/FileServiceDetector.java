package com.xiaotao.saltedfishcloud.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileServiceDetector implements InitializingBean {
    private final StoreServiceProvider storeServiceProvider;
    private final DiskFileSystemProvider fileSystemFactory;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("[CORE]file-system-factory  ：{}", fileSystemFactory.getClass().getSimpleName());
        log.info("[CORE]file-system          ：{}", fileSystemFactory.getFileSystem().getClass().getSimpleName());
        log.info("[CORE]store-service-factory：{}", storeServiceProvider.getClass().getSimpleName());
        log.info("[CORE]store-service        ：{}", storeServiceProvider.getService().getClass().getSimpleName());
    }
}
