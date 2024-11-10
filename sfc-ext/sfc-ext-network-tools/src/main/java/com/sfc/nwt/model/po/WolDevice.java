package com.sfc.nwt.model.po;


import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

import jakarta.persistence.*;
import java.util.Date;

/**
 * 需要WOL的设备
 */
@Getter
@Setter
@Entity
@Table(indexes = {
        @Index(name = "idx_uid", columnList = "uid")
})
public class WolDevice extends AuditModel {

    /**
     * 设备命名
     */
    private String name;

    /**
     * mac地址
     */
    @Length(max = 17, min = 17)
    @Column(length = 17)
    private String mac;

    /**
     * ip地址
     */
    private String ip;

    /**
     * 端口
     */
    private Integer port;

    /**
     * WOL魔术包发送的IP（公网下唤醒需要指定目标公网IP，局域网下使用广播地址即可）
     */
    private String sendIp;

    /**
     * 上次发送唤醒的时间
     */
    private Date lastWakeAt;

    /**
     * 设备是否在线
     */
    @Transient
    private Boolean isOnline;

    /**
     * 排序
     */
    private Integer showOrder;
}
