package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.third.model.ThirdPartyPlatformCallbackResult;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public interface ThirdPartyPlatformManager {
    /**
     * 注册一个第三方平台处理器
     */
    void registerPlatformHandler(ThirdPartyPlatformHandler handler);

    /**
     * 获取所有已注册的第三方平台处理器
     */
    List<ThirdPartyPlatformHandler> getAllPlatformHandler();

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

    /**
     * 绑定第三方账号到已有的系统账号
     * @param actionId {@link #doCallback(String, HttpServletRequest)}中的操作id
     * @param user      要绑定的用户
     */
    void bindUser(String actionId, User user);

    /**
     * 根据第三方账号创建账号
     * @param actionId {@link #doCallback(String, HttpServletRequest)}中的操作id
     * @return 创建出来的新账号
     */
    User createUser(String actionId);

    /**
     * 列出系统当前可用的第三方平台（第三方平台已注册处理器 且 配置为已启用）
     */
    List<ThirdPartyAuthPlatform> listPlatform();
}
