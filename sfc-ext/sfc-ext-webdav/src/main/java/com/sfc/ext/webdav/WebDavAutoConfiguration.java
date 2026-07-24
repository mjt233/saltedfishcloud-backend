package com.sfc.ext.webdav;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.sfc.ext.webdav.controller.WebDavAuthController;
import com.sfc.ext.webdav.dao.WebDavAuthRepo;
import com.sfc.ext.webdav.model.po.WebDavAuth;
import com.sfc.ext.webdav.model.property.WebDavProperty;
import com.sfc.ext.webdav.core.WebDavManagerService;
import com.sfc.ext.webdav.model.property.WebDavPropertyVO;
import com.sfc.ext.webdav.service.WebDavAuthService;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import io.milton.common.StreamUtils;
import io.milton.http.http11.auth.DigestGenerator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Configuration
@EnableJpaRepositories(basePackageClasses = WebDavAuthRepo.class)
@EntityScan(basePackageClasses = WebDavAuth.class)
@Import({
        WebDavAuthController.class,
        DigestGenerator.class,
        WebDavAuthService.class
})
public class WebDavAutoConfiguration implements ApplicationRunner {

    @Bean
    public WebDavProperty webDavProperty(ConfigService configService, HelloService helloService) {
        WebDavProperty property = new WebDavProperty();
        configService.bindPropertyEntity(property);
        WebDavPropertyVO vo = new WebDavPropertyVO(property);
        helloService.setFeature("webDavConfig", () -> vo);
        return property;
    }

    @Bean
    public WebDavManagerService webDavManagerService(WebDavProperty webDavProperty, ConfigService configService) {
        WebDavManagerService managerService = new WebDavManagerService(webDavProperty);
        AtomicLong lastResetTime = new AtomicLong();
        Runnable resetTomcat = () -> {
            try {
                long now = System.currentTimeMillis();
                if (now - lastResetTime.get() < 1000) {
                    log.warn("WebDAV 配置变更间隔小于1s，忽略响应");
                    return;
                }
                if (managerService.isRunning()) {
                    managerService.stop();
                }
                if (Boolean.TRUE.equals(webDavProperty.getIsEnable())) {
                    managerService.start();
                }
                lastResetTime.set(now);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        configService.addAfterSetListener(WebDavProperty::getListenPort, port -> resetTomcat.run());
        configService.addAfterSetListener(WebDavProperty::getIsEnable, port -> resetTomcat.run());
        resetTomcat.run();
        return managerService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(StreamUtils.class);
        logger.setLevel(Level.OFF);
    }
}
