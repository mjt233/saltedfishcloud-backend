package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import com.xiaotao.saltedfishcloud.validator.annotations.Username;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Date;

/**
 * 用户表实体类
 */
@Entity(name = "user")
@Table(name = "user", indexes = {
        @Index(name = "user_index", columnList = "user", unique = true),
        @Index(name = "mail_index", columnList = "email")
})
@Getter
@Setter
public class UserInfo extends BaseModel {
    @Username
    protected String user;

    @JsonIgnore
    protected String pwd;

    /**
     * 用户权限类型
     */
    protected Integer type = User.TYPE_COMMON;

    /**
     * 用户id，后续将改为Long类型
     */
    protected Long id;

    /**
     * 上次登录日期
     */
    protected Integer lastLogin;

    /**
     * 空间配额（暂时没用上）
     */
    protected Integer quota;

    /**
     * 绑定邮箱
     */
    protected String email;

    /**
     * 注册日期
     */
    protected Date createAt;

}
