package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;

import java.util.List;

public interface ProxyInfoService extends CrudService<ProxyInfo> {
    /**
     * 测试代理是否可用
     * @param proxyId   代理id
     * @param timeout   超时判定时间，单位ms
     * @param useCache  是否使用缓存的结果
     * @return          是否可用
     */
    boolean testProxy(Long proxyId, int timeout, boolean useCache);

    /**
     * 获取当前用户可用的代理列表
     * <p>返回当前用户可见的代理（公共代理 + 当前用户的私有代理），非管理员用户无法看到受保护的公共代理。</p>
     * @return 可用代理列表
     */
    List<ProxyInfo> listAvailableProxy();
}
