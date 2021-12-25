package com.xiaotao.saltedfishcloud.service.mail;

import com.xiaotao.saltedfishcloud.exception.JsonException;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

/**
 * 消息生成器接口的实现类，通过thymeleaf模板引擎生成邮件正文内容
 */
@Component
@RequiredArgsConstructor
public class TemplateMailMessageGenerator implements MailMessageGenerator {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailProperties properties;

    /**
     * 创建一个邮件消息，根据系统配置自动填写发信人，回信地址
     * @param to        收件地址
     * @param subject   邮件主题
     * @param content   正文内容
     */
    protected MimeMessage createMessage(String to, String subject, String content) throws UnsupportedEncodingException, MessagingException {
        if (!properties.isValid()) {
            throw new JsonException(500, "咸鱼云邮件发信配置不正确");
        }
        MimeMessageHelper message = new MimeMessageHelper(mailSender.createMimeMessage(), true, "UTF-8");
        String alias = properties.getAlias();
        if (alias != null) {
            message.setFrom(properties.getFrom(), alias);
        } else {
            message.setFrom(properties.getFrom());
        }

        message.setText(content, true);
        message.setTo(to);
        message.setReplyTo(properties.getReply());
        message.setSubject(subject);
        return message.getMimeMessage();
    }

    /**
     * 处理邮件模板，填充内容
     * @param code  验证码
     * @param usage 用途
     * @return      模板处理后的html正文内容
     */
    private String processTemplate(String code, String usage) {
        Context context = new Context();
        context.setVariable("code", code);
        context.setVariable("usage", usage);
        return templateEngine.process("mailcode", context);
    }

    @Override
    public MimeMessage getRegCodeMessage(String to, String code) throws MessagingException, UnsupportedEncodingException {
        return createMessage(to, "咸鱼云账号注册", processTemplate(code, "咸鱼云账号注册"));
    }

    @Override
    public MimeMessage getFindPasswordCodeMessage(String to, String code) throws MessagingException, UnsupportedEncodingException {
        return createMessage(to, "咸鱼云找回密码", processTemplate(code, "咸鱼云找回密码"));
    }
}
