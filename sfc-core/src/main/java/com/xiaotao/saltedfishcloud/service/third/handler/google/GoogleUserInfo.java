package com.xiaotao.saltedfishcloud.service.third.handler.google;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;


@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GoogleUserInfo {
    /**
     * 用户ID
     */
    private String sub;

    /**
     * 姓名
     */
    private String name;

    /**
     * 名
     */
    private String givenName;

    /**
     * 姓
     */
    private String familyName;

    /**
     * Google账号头像URL
     */
    private String picture;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 邮箱是否已验证
     */
    private Boolean emailVerified;

    /**
     * 地区
     */
    private String locale;
}