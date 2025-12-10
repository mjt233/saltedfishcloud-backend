package com.xiaotao.saltedfishcloud.model.po;


import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import com.xiaotao.saltedfishcloud.service.log.LogLevel;
import jakarta.persistence.*;
import lombok.*;

/**
 * 系统日志记录服务
 */
@Entity
@Table(
        indexes = {
                @Index(name = "idx_create", columnList = "create_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogRecord extends AuditModel {
    /**
     * 日志类型
     */
    @Column(nullable = false)
    private String type;

    /**
     * 日志级别
     */
    @Enumerated(EnumType.STRING)
    private LogLevel level;

    /**
     * 产生日志的主机节点
     */
    private String producerHost;

    /**
     * 产生日志的服务进程id
     */
    private Long producerPid;

    /**
     * 产生线程
     */
    private String producerThread;

    /**
     * 日志摘要
     */
    @Lob
    private String msgAbstract;

    /**
     * 客户端请求的IP地址
     */
    private String ip;

    /**
     * 消息详情
     */
    @Lob
    private String msgDetail;
}
