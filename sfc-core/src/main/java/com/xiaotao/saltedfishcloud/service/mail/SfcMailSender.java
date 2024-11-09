package com.xiaotao.saltedfishcloud.service.mail;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.internet.MimeMessage;

/**
 * 咸鱼云邮件发送器(Salted fish cloud mail sender)
 * 继承自JavaMailSenderImpl，重写了doSend方法实现支持动态设置发信服务器配置参数功能
 */
public class SfcMailSender extends JavaMailSenderImpl {
    private final MailProperties configuration;


    public SfcMailSender(MailProperties configuration) {
        this.configuration = configuration;
    }


    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) throws MailException {
        this.setProtocol(configuration.getProtocol().toLowerCase());
        this.setHost(configuration.getHost());
        this.setUsername(configuration.getUsername());
        this.setPassword(configuration.getPassword());
        this.setPort(configuration.getPort());
        super.doSend(mimeMessages, originalMessages);
    }
}
