package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;

public interface ProxyInfoService extends CrudService<ProxyInfo> {
    /**
     * 测试代理是否可用
     * @param proxyId   代理id
     * @param timeout   超时判定时间，单位ms
     * @param useCache  是否使用缓存的结果
     * @return          是否可用
     */
    boolean testProxy(Long proxyId, int timeout, boolean useCache);
}
