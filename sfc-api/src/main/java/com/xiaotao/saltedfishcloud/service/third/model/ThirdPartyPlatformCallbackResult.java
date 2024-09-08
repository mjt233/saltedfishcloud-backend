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
     * 认证成功后对应的咸鱼云系统用户
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
     * 是否为新用户
     */
    private Boolean isNewUser;
}
