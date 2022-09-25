package com.xiaotao.saltedfishcloud.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileServiceDetector implements ApplicationRunner {
    private final StoreServiceFactory storeServiceFactory;
    private final DiskFileSystemManager fileSystemFactory;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("[CORE]file-system-factory  ：{}", fileSystemFactory.getClass().getSimpleName());
        log.info("[CORE]file-system          ：{}", fileSystemFactory.getMainFileSystem().getClass().getSimpleName());
        log.info("[CORE]store-service-factory：{}", storeServiceFactory.getClass().getSimpleName());
        log.info("[CORE]store-service        ：{}", storeServiceFactory.getService().getClass().getSimpleName());
    }
}
