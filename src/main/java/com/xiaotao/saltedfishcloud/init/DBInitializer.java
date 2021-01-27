package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.service.file.PathMapService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 用于初始化数据库信息
 */
@Component
public class DBInitializer implements ApplicationRunner {
    @Resource
    PathMapService pathMapService;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        pathMapService.setRecord("/");
    }
}
