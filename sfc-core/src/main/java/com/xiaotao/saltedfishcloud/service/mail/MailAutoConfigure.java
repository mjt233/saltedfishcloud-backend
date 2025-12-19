package com.xiaotao.saltedfishcloud.service.mail;

import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.log.LogRecordManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * 邮件服务相关Bean配置类
 * 主要配置邮件发信参数类和MailSender
 */
@Configuration
@Slf4j
public class MailAutoConfigure implements ApplicationRunner {

    @Autowired
    @Lazy
    private ConfigService configService;

    @Autowired
    @Lazy
    private LogRecordManager logRecordManager;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        this.configureProperties();
    }

    private void configureProperties() {

        // 监听发信服务器的配置信息修改，更新发信服务器配置bean信息
        configService.addAfterSetListener(SysCommonConfig::getMailProperties, e -> {
            BeanUtils.copyProperties(e, this.mailProperties());
            javaMailSender().onConfigurationChange(e);
            log.debug("邮件发信服务器配置更改：{}", e);
        });
    }

    @Bean
    public MailProperties mailProperties() {
        return configService.getConfig(SysCommonConfig::getMailProperties);
    }

    @Bean
    public SfcMailSender javaMailSender() {
        MailProperties properties = configService.getConfig(SysCommonConfig::getMailProperties);
        if (!properties.isValid()) {
            log.warn("邮件发信服务器信息参数无效，需要进行配置");
        }
        SfcMailSender sender = new SfcMailSender(logRecordManager);
        sender.loadConfiguration(properties);
        return sender;
    }
}
