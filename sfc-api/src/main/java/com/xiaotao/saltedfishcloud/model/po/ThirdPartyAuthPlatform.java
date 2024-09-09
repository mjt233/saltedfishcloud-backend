package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * 第三方认证平台配置
 */
@Entity
@Table(
        indexes = {
                @Index(name = "idx_type", columnList = "type", unique = true)
        }
)
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ThirdPartyAuthPlatform extends AuditModel {
    /**
     * 第三方平台类型
     */
    @Column(unique = true, nullable = false)
    private String type;

    /**
     * 平台展示用的名称
     */
    private String name;

    /**
     * 图标 可以是http链接/mdi标识/base64
     */
    private String icon;

    /**
     * 是否已启用
     */
    private Boolean isEnable;

    /**
     * json格式配置参数
     */
    @Column(length = 2048)
    private String config;

    /**
     * 是否允许通过该平台注册为新用户
     */
    private Boolean isAllowRegister;
}
