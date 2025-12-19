package com.xiaotao.saltedfishcloud.service.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.service.log.LogLevel;
import com.xiaotao.saltedfishcloud.service.log.LogRecordManager;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 咸鱼云邮件发送器(Salted fish cloud mail sender)
 * 继承自JavaMailSenderImpl，重写了doSend方法实现支持动态设置发信服务器配置参数功能
 */
@Slf4j
@RequiredArgsConstructor
public class SfcMailSender extends JavaMailSenderImpl {
    private static final String LOG_PREFIX = "[发送邮件]";
    private static final String DEFAULT_TLS_PROTOCOL = "TLSv1.2";

    private final LogRecordManager logRecordManager;


    private String getAllRecipientsStr(MimeMessage m) throws MessagingException {
        return Arrays.stream(m.getAllRecipients()).map(Objects::toString).collect(Collectors.joining(";"));
    }

    /**
     * 构造 MimeMultipart 类型邮件正文的人类友好阅读格式文本，用于记录发信日志
     * @param m 消息
     * @return 消息的文本内容
     */
    private String getMimeMultipartTextContent(MimeMultipart m) throws MessagingException, IOException {
        StringBuilder sb = new StringBuilder();
        StringBuilder attachFileName = new StringBuilder();
        int count = m.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = m.getBodyPart(i);
            String fileName = bodyPart.getFileName();
            if (fileName != null) {
                attachFileName.append("附件: ").append(fileName).append("\n");
                continue;
            }
            Object partContent = bodyPart.getContent();
            if (partContent instanceof MimeMultipart) {
                sb.append(getMimeMultipartTextContent((MimeMultipart) partContent)).append("\n");
            } else {
                sb.append(partContent);
            }
        }
        return sb.append(attachFileName).toString();
    }

    /**
     * 发信配置变更回调
     * @param configuration 变更后的发信配置
     */
    public void onConfigurationChange(MailProperties configuration) {
        this.loadConfiguration(configuration);
    }

    /**
     * 加装发信配置
     * @param configuration 发信配置
     */
    public void loadConfiguration(MailProperties configuration) {
        log.info("{}加载邮件发信配置: {}", LOG_PREFIX, configuration);
        this.setProtocol(configuration.getProtocol().toLowerCase());
        this.setHost(configuration.getHost());
        this.setUsername(configuration.getUsername());
        this.setPassword(configuration.getPassword());
        this.setPort(configuration.getPort());

        Properties props = new Properties();
        if (Boolean.TRUE.equals(configuration.getUseSSL())) {
            props.put("mail.smtp.auth", true);
            props.put("mail.smtp.ssl.enable", true);
            props.put(
                    "mail.smtp.ssl.protocols",
                    Optional.ofNullable(configuration.getTlsProtocols())
                            .filter(StringUtils::hasText)
                            .orElse(DEFAULT_TLS_PROTOCOL)
            );
            props.put("mail.smtp.socketFactory.class", SSLSocketFactory.class.getName());
            props.put("mail.smtp.socketFactory.fallback", "false");
        }
        this.setJavaMailProperties(props);
    }

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) throws MailException {
        RuntimeException exception = null;
        try {
            super.doSend(mimeMessages, originalMessages);
        } catch (RuntimeException e) {
            exception = e;
            throw e;
        } finally {
            this.addMailSendRecord(mimeMessages, exception);
        }
    }

    /**
     * 添加邮件发送记录日志
     * @param mimeMessages  发送的消息
     * @param exception 发送异常
     */
    protected void addMailSendRecord(MimeMessage[] mimeMessages, RuntimeException exception) {
        // ===== 记录本次发送邮件日志 ====
        Map<String, Object> detail = new HashMap<>();
        boolean isSuccess = exception == null;
        detail.put("isSuccess", isSuccess);
        StringBuilder error = new StringBuilder(Optional.ofNullable(exception).map(Throwable::getMessage).orElse(""));
        detail.put("error", error);

        // 提取正文
        List<String> content = Arrays.stream(mimeMessages).map(m -> {
            try {
                String contentStr;
                Object contentObj = m.getContent();
                if (contentObj instanceof MimeMultipart) {
                    contentStr = getMimeMultipartTextContent((MimeMultipart) contentObj);
                } else {
                    contentStr = contentObj.toString();
                }
                return String.format("""
                            subject: %s
                            recipients: %s
                            
                            ====content begin===
                            %s
                            ====content end===
                            """,
                        m.getSubject(), getAllRecipientsStr(m), contentStr
                );
            } catch (Exception e) {
                log.error("{}记录邮件发送日志时获取正文出错", LOG_PREFIX, e);
                return e.getMessage();
            }
        }).toList();
        detail.put("content", content);

        // 提取标题
        String msgAbstract = Arrays.stream(mimeMessages).map(m -> {
            try {
                return getAllRecipientsStr(m) + ":" + m.getSubject();
            } catch (MessagingException e) {
                log.error("记录邮件发送日志时获取标题出错", e);
                return e.getMessage();
            }
        }).collect(Collectors.joining("; "));
        detail.put("subject", msgAbstract);

        // 组装最终明细
        String msgDetail;
        try {
            msgDetail = MapperHolder.toJson(detail);
        } catch (JsonProcessingException | RuntimeException e) {
            error.append("; ").append(e.getMessage());
            log.error("{}记录邮件发送日志序列化失败", LOG_PREFIX, e);
            msgDetail = "{\"isSuccess\": false, \"error\": \"序列化失败: " + error.toString().replaceAll("\"", "\\\"") + "\"}";
        }
        logRecordManager.saveRecord(LogRecord.builder()
                .type("发送邮件")
                .msgAbstract(msgAbstract)
                .msgDetail(msgDetail)
                .level(isSuccess ? LogLevel.INFO : LogLevel.ERROR)
                .build());
    }
}
