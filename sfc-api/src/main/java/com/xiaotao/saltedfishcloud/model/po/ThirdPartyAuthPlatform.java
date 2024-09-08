package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * 第三方认证平台配置
 */
@Entity
@Table(
        indexes = {
                @Index(name = "idx_type", columnList = "type")
        }
)
@Setter
@Getter
public class ThirdPartyAuthPlatform extends AuditModel {
    /**
     * 第三方平台类型
     */
    private String type;

    /**
     * 是否已启用
     */
    private Boolean isEnable;

    /**
     * json格式配置参数
     */
    private String config;

    /**
     * 是否允许通过该平台注册为新用户
     */
    private Boolean isAllowRegister;
}
