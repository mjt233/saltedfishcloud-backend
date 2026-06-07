package com.sfc.pxeboot;

import com.sfc.pxeboot.controller.PxeBootController;
import com.sfc.pxeboot.server.ipxe.IpxeScriptEngine;
import com.sfc.pxeboot.server.iso.JavaIsoHandler;
import com.sfc.pxeboot.server.proxydhcp.ProxyDhcpServer;
import com.sfc.pxeboot.server.tftp.PxeTftpServer;
import com.sfc.pxeboot.server.tftp.TftpFileProvider;
import com.sfc.pxeboot.service.BootItemServiceImpl;
import com.sfc.pxeboot.service.IsoResourceExtractorService;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * PXE 网络启动插件自动配置类
 */
@Configuration
@EntityScan("com.sfc.pxeboot.model.po")
@EnableJpaRepositories("com.sfc.pxeboot.repo")
@Import({
        TftpFileProvider.class,
        BootItemServiceImpl.class,
        JavaIsoHandler.class,
        IpxeScriptEngine.class,
        PxeTftpServer.class,
        ProxyDhcpServer.class,
        IsoResourceExtractorService.class,
        PxeBootController.class
})
public class PxeBootAutoConfiguration {

    @Bean
    public PxeBootProperty pxeBootProperty(ConfigService configService) {
        PxeBootProperty property = new PxeBootProperty();
        configService.bindPropertyEntity(property);
        return property;
    }
}
