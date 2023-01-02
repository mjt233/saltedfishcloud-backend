package com.xiaotao.saltedfishcloud.service.desktop;

import com.xiaotao.saltedfishcloud.model.DesktopComponent;

import java.util.List;

/**
 * 桌面组件库服务
 */
public interface DesktopComponentService {
    /**
     * 注册一个组件
     * @param component 组件配置
     */
    void registerComponent(DesktopComponent component);

    /**
     * 获取所有已注册组件
     */
    List<DesktopComponent> listAllComponents();
}
