package com.xiaotao.saltedfishcloud.service.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@RequiredArgsConstructor
public class MailConfiguration {
    private final ConfigDao configDao;

    @Bean
    public MailProperties mailProperties() {
        String configure = configDao.getConfigure(ConfigName.MAIL_PROPERTIES);
        if (configure != null) {
            try {
                return MapperHolder.mapper.readValue(configure, MailProperties.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return new MailProperties();
            }
        } else {
            return new MailProperties();
        }
    }

    @Bean
    public JavaMailSender javaMailSender() {
        return new SfcMailSender(mailProperties());
    }
}
