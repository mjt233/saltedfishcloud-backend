package com.xiaotao.saltedfishcloud.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileServiceDetector implements ApplicationRunner {
    private final StoreServiceProvider storeServiceProvider;
    private final DiskFileSystemProvider fileSystemFactory;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("[CORE]file-system-factory  ：{}", fileSystemFactory.getClass().getSimpleName());
        log.info("[CORE]file-system          ：{}", fileSystemFactory.getFileSystem().getClass().getSimpleName());
        log.info("[CORE]store-service-factory：{}", storeServiceProvider.getClass().getSimpleName());
        log.info("[CORE]store-service        ：{}", storeServiceProvider.getService().getClass().getSimpleName());
    }
}
