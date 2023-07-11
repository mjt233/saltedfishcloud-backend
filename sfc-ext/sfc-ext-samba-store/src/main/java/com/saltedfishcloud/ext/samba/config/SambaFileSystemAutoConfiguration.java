package com.saltedfishcloud.ext.samba.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SambaFileSystemAutoConfiguration implements InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        log.warn("[Samba存储]samba存储插件未开发完成，暂时不可用。");
    }
    //    @Autowired
//    private DiskFileSystemManager diskFileSystemManager;
//
//    @Bean
//    public SambaDiskFileSystemFactory sambaDiskFileSystemFactory() {
//        SambaDiskFileSystemFactory factory = new SambaDiskFileSystemFactory();
//        diskFileSystemManager.registerFileSystem(factory);
//        return factory;
//    }
}
