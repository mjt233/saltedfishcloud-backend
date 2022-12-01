package com.xiaotao.saltedfishcloud.common;

import com.xiaotao.saltedfishcloud.model.ConfigNode;

import java.util.List;
import java.util.Map;

/**
 * 系统管理后台预览项目提供者
 */
public interface SystemOverviewItemProvider {

    /**
     * 提供可用的预览项目，返回值将作为提供出去的预览项，深度为2，最外层为分类，内层为各个具体项目
     * @param existItem 系统已存在的各个大类预览项
     * @return      提供出去的预览项
     */
    List<ConfigNode> provideItem(Map<String, ConfigNode> existItem);

    /**
     * 获取提供顺序，越小越优先
     */
    default long getProvideOrder() {
        return 0;
    }
}
