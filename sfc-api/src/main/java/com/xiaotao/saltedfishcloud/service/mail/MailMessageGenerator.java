package com.xiaotao.saltedfishcloud.service.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

/**
 * 邮件发信消息生成接口
 */
public interface MailMessageGenerator {
    /**
     * 生成通过邮件接收的账号注册验证码消息
     * @param to    收件人地址
     * @param code  注册码
     */
    MimeMessage getRegCodeMessage(String to, String code) throws MessagingException, UnsupportedEncodingException;


    /**
     * 生成通过邮件接收的账号找回密码验证码消息
     * @param to    收件人地址
     * @param code  注册码
     */
    MimeMessage getFindPasswordCodeMessage(String to, String code) throws MessagingException, UnsupportedEncodingException;

    MimeMessage getBindNewMailCodeMessage(String to, String code) throws MessagingException, UnsupportedEncodingException;

    /**
     * 生成旧邮箱验证邮件
     * @param to    接收邮箱
     * @param code  邮箱验证码
     */
    MimeMessage getVerifyMailCodeMessage(String to, String code) throws MessagingException, UnsupportedEncodingException;
}
