package com.xiaotao.saltedfishcloud.service.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 邮件服务相关Bean配置类
 * 主要配置邮件发信参数类和MailSender
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MailConfiguration {
    private final ConfigDao configDao;

    @Bean
    public MailProperties mailProperties() {
        String configure = configDao.getConfigure(ConfigName.MAIL_PROPERTIES);
        MailProperties res;
        if (configure != null) {
            try {
                res = MapperHolder.mapper.readValue(configure, MailProperties.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                res = new MailProperties();
            }
        } else {
            res = new MailProperties();
        }

        if (!res.isValid()) {
            log.warn("邮件发信服务器信息参数无效，需要进行配置");
        }
        return res;
    }

    @Bean
    public JavaMailSender javaMailSender() {
        return new SfcMailSender(mailProperties());
    }
}
