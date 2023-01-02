package com.xiaotao.saltedfishcloud.service.desktop;

import com.xiaotao.saltedfishcloud.model.po.DesktopComponentConfig;

import java.util.List;

/**
 * 桌面组件使用配置服务
 */
public interface DesktopComponentConfigService {
    /**
     * 获取用户配置的桌面小组件
     */
    List<DesktopComponentConfig> listByUid(Long uid);

    /**
     * 保存组件应用
     */
    void save(DesktopComponentConfig componentConfig);

    /**
     * 移除组件
     */
    void remove(Long id);
}
