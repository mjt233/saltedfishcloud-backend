package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.model.po.User;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

public interface ThirdPartyPlatformHandler {
    /**
     * 平台类型标识
     */
    String getType();

    /**
     * 获取第三方平台的登录url
     */
    String getRedirectUrl(ThirdPartyAuthPlatform partyAuthPlatform);

    /**
     * 第三方平台授权完成回调
     * @param partyAuthPlatform         第三方平台配置参数
     * @param platformCallbackParam     第三方平台回调参数
     * @return                          第三方授权用户
     */
    ThirdPartyPlatformUser callback(ThirdPartyAuthPlatform partyAuthPlatform, Map<String, Object> platformCallbackParam) throws IOException;

    /**
     * 获取平台默认配置
     */
    ThirdPartyAuthPlatform getDefaultConfig();

    /**
     * 获取平台的表单配置信息，用于在管理端中呈现
     */
    List<ConfigNode> getPlatformConfigNode();
}
