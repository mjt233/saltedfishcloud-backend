package com.sfc.cloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * 用户身份模型：网关注入下游的「当前用户主体」。
 */
@Data
public class UserPrincipal {
    /** 用户唯一标识 */
    private Long id;

    /** 用户登录名，JSON 序列化时映射为 {@code "user"} */
    @JsonProperty("user")
    private String username;

    /** 用户邮箱 */
    private String email;

    /** 用户配额（字节） */
    private Long quota;

    /** 最后登录时间戳（秒） */
    private Integer lastLogin;

    /** 账号创建时间 */
    private Date createAt;
}
