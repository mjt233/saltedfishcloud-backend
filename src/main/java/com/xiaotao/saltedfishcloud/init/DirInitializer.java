package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;

/**
 * 用于启动的时候检查配置文件中的路径是否存在，若不存在则创建文件夹
 */
@Component
@Slf4j
public class DirInitializer implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        File[] files = {
                new File(DiskConfig.PUBLIC_ROOT),
                new File(DiskConfig.STORE_ROOT),
                new File(DiskConfig.STORE_ROOT + "/user_file"),
                new File(DiskConfig.STORE_ROOT + "/user_profile")
        };
        Arrays.stream(files).forEach(file -> {
            if (!file.exists()) {
                log.warn("文件夹" + file.getPath() + "不存在，将被创建");
                file.mkdirs();
            }
        });
    }
}
