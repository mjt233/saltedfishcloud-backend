package com.xiaotao.saltedfishcloud.service.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.constant.SysConfigName;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
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

    @Override
    public void run(ApplicationArguments args) throws Exception {
        this.configureProperties();
    }

    private void configureProperties() {
        String configure = configService.getConfig(SysConfigName.Common.MAIL_PROPERTIES);
        if (configure != null) {
            try {
                MailProperties properties = MapperHolder.mapper.readValue(configure, MailProperties.class);
                BeanUtils.copyProperties(properties, this.mailProperties());
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        if (!this.mailProperties().isValid()) {
            log.warn("邮件发信服务器信息参数无效，需要进行配置");
        }

        // 监听发信服务器的配置信息修改，更新发信服务器配置bean信息
        configService.addAfterSetListener(SysConfigName.Common.MAIL_PROPERTIES, e -> {
            try {
                MailProperties newVal = MapperHolder.mapper.readValue(e, MailProperties.class);
                BeanUtils.copyProperties(newVal, this.mailProperties());
                log.debug("邮件发信服务器配置更改：{}", e);
            } catch (JsonProcessingException ex) {
                ex.printStackTrace();
            }
        });
    }

    @Bean
    public MailProperties mailProperties() {
        return new MailProperties();
    }

    @Bean
    public JavaMailSender javaMailSender() {
        return new SfcMailSender(mailProperties());
    }
}
