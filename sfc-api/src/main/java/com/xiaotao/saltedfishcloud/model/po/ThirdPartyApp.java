package com.xiaotao.saltedfishcloud.model.po;


import com.xiaotao.saltedfishcloud.constant.ByteSize;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

/**
 * 第三方平台应用
 */
@Entity
@Table(name = "third_party_app", indexes = {
    @Index(name = "idx_third_party_app_name", columnList = "name", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
public class ThirdPartyApp extends AuditModel {

    /**
     * 应用名称
     */
    @NotBlank
    private String name;

    /**
     * 用户确认授权后的回调URL，可为空（为空时授权接口需要由调用方通过参数传入重定向URL）
     */
    @URL
    @Column(length = 1024)
    private String callbackUrl;

    /**
     * 应用介绍
     */
    @Column(length = 1024)
    private String describeContent;

    /**
     * 联系邮箱
     */
    private String email;

    /**
     * 应用图标(URL 支持base64)
     */
    @Lob
    @Length(max = ByteSize._1KiB * 512)
    private String icon;

    /**
     * 是否已启用
     */
    private Boolean isEnabled;

    /**
     * 是否允许第三方应用申请永久有效的 ApiTicket
     */
    private Boolean allowPermanentApiTicket;
}
