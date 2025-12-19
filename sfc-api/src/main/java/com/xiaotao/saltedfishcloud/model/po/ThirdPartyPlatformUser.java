package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 第三方用户登录认证
 */
@Entity
@Getter
@Setter
@Table(
        indexes = {
                @Index(name = "idx_uid", columnList = "uid"),
                @Index(name = "idx_pid", columnList = "thirdPartyUserId,platformType")
        }
)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyPlatformUser extends AuditModel {
    /**
     * 第三方平台类型
     */
    private String platformType;

    /**
     * 第三方账号用户名
     */
    private String userName;

    /**
     * 第三方平台账号绑定的邮箱，可能为空
     */
    private String email;

    /**
     * 是否已激活。
     * 需要完成以下步骤才能激活：
     * 1. 已有的咸鱼云账号绑定第三方平台
     * 2. 直接使用第三方平台新登录的账号，作为全新账号注册
     */
    private Boolean isActive;

    /**
     * 启用状态（1 - 启用， 0 - 停用）
     */
    private Integer status;

    /**
     * 第三方平台账号的唯一标识
     */
    private String thirdPartyUserId;
}
