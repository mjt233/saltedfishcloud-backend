package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import com.xiaotao.saltedfishcloud.validator.annotations.Username;
import lombok.Getter;
import lombok.Setter;

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
    /**
     * 用户名
     */
    @Username
    protected String user;

    /**
     * 密码的加盐哈希
     */
    @JsonIgnore
    protected String pwd;

    /**
     * 用户权限类型
     */
    protected Integer type = User.TYPE_COMMON;

    /**
     * 上次登录日期
     */
    protected Integer lastLogin;

    /**
     * 空间配额（单位：GiB，暂时没用上）
     */
    protected Long quota;

    /**
     * 绑定邮箱
     */
    protected String email;

    /**
     * 注册日期
     */
    protected Date createAt;

}
