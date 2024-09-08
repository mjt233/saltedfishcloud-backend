package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.third.model.ThirdPartyPlatformCallbackResult;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public interface ThirdPartyPlatformManager {
    /**
     * 注册一个第三方平台处理器
     */
    void registerPlatform(ThirdPartyPlatformHandler handler);

    /**
     * 获取所有已注册的第三方平台处理器
     */
    List<ThirdPartyPlatformHandler> getAllPlatform();

    /**
     * 获取第三方平台处理器
     * @param platformType      平台类型标识
     */
    ThirdPartyPlatformHandler getHandler(String platformType);

    /**
     * 执行回调处理
     * @param platformType  平台类型
     * @param request       来自第三方平台的回调请求
     */
    ThirdPartyPlatformCallbackResult doCallback(String platformType, HttpServletRequest request);


}
