package com.sfc.nwt.model.po;


import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;
import java.util.Date;

/**
 * 需要WOL的设备
 */
@Getter
@Setter
@Entity
public class WolDevice extends AuditModel {

    /**
     * 设备命名
     */
    private String name;

    /**
     * mac地址
     */
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
