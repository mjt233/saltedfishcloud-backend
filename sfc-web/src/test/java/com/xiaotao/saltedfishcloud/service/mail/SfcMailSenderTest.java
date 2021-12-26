package com.xiaotao.saltedfishcloud.service.mail;

import com.xiaotao.saltedfishcloud.service.config.ConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.MessagingException;
import java.io.UnsupportedEncodingException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SfcMailSenderTest {
    @Autowired
    private ConfigServiceImpl configService;
    @Autowired
    private JavaMailSender mailSender;

    @Test
    public void test() throws MessagingException, UnsupportedEncodingException {
        MimeMessageHelper message = new MimeMessageHelper(mailSender.createMimeMessage(), true);

        message.setFrom("sfc@xiaotao233.top", "我是别名ohhhhh");
        message.setReplyTo("mjt233@qq.com");
        message.setTo("mjt233@qq.com");
        message.setSubject("测试");
        message.setText("ohhhhhhhhhhhhhhhhhh");
        assertNotNull(mailSender);



        MailProperties mailProperties = new MailProperties();
        mailProperties.setHost("smtpdm.aliyun.com");
        mailProperties.setPort(25);
        mailProperties.setUsername("sfc@xiaotao233.top");
        mailProperties.setPassword("MOjintao233");
        assertTrue(configService.setMailProperties(mailProperties));

        mailSender.send(message.getMimeMessage());
    }
}
