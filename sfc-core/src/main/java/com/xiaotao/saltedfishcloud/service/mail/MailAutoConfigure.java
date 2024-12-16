package com.xiaotao.saltedfishcloud.service.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.constant.SysConfigName;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.log.LogRecordManager;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.javamail.JavaMailSender;

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
        MailProperties properties = configService.getConfig(SysCommonConfig::getMailProperties);
        if (properties != null) {
            BeanUtils.copyProperties(properties, this.mailProperties());
        }

        if (!this.mailProperties().isValid()) {
            log.warn("邮件发信服务器信息参数无效，需要进行配置");
        }

        // 监听发信服务器的配置信息修改，更新发信服务器配置bean信息
        configService.addAfterSetListener(SysCommonConfig::getMailProperties, e -> {
            BeanUtils.copyProperties(e, this.mailProperties());
            log.debug("邮件发信服务器配置更改：{}", e);
        });
    }

    @Bean
    public MailProperties mailProperties() {
        return new MailProperties();
    }

    @Bean
    public JavaMailSender javaMailSender() {
        return new SfcMailSender(mailProperties(), logRecordManager);
    }
}
