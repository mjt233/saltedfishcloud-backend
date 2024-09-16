package com.xiaotao.saltedfishcloud.service.third.model;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.model.po.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 第三方平台认证回调结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyPlatformCallbackResult {

    /**
     * 认证成功后已关联或即将关联的咸鱼云系统用户。
     * 当该字段有值，但isNewUser为true时，表示一个已登录的用户操作了第三方登录或系统中已存在相同邮箱的账号，可能需要进行关联操作。前端在确认关联时需要比对是否与页面当前用户一致，不一致时应拒绝操作。
     */
    private User user;

    /**
     * 认证成功后的平台用户信息
     */
    private ThirdPartyPlatformUser platformUser;

    /**
     * 是否需要转跳URL
     */
    private String redirectUrl;

    /**
     * 是否为新用户。新用户则表示首次通过第三方登录登入系统，未关联或创建咸鱼云账号
     */
    private Boolean isNewUser;

    /**
     * 本次登录操作id
     */
    private String actionId;

    private String newToken;
}
