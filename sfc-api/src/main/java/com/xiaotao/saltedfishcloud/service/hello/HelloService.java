package com.xiaotao.saltedfishcloud.service.hello;

import java.util.Map;

/**
 * 问好服务，让客户端了解服务器的基本概况以调整自身的相关参数和功能特性
 */
public interface HelloService {
    /**
     * 追加注册一个特性详情，该特性的所有特性详情将作为一个集合存在，若同名特性已注册存在，则往集合中追加。
     * @param name      特性名称
     * @param detail    特性详情描述对象
     */
    void appendFeatureDetail(String name, Object detail);

    /**
     * 设置一个特性详情。若已存在特性，详情信息对象将会被完全覆盖
     * @param name      特性名称
     * @param detail    特性详情描述对象
     */
    void setFeature(String name, Object detail);

    /**
     * 获取指定特性的详情引用对象。
     * 注意返回值是详情对象的引用，而不是视图或副本。这也意味着可以通过返回值对特性进行修改
     * @param name  特性名称
     * @return      特性详情描述引用对象
     */
    Object getDetail(String name);

    /**
     * 将一个配置项的值绑定到feature中。feature中的值将保持和配置项的值同步。
     * @param configKey     配置项key
     * @param mapKey        绑定映射的特性key
     * @param type          数据类型
     */
    void bindConfigAsFeature(String configKey, String mapKey, Class<?> type);

    /**
     * 获取所有特性详情
     * @return  key为特性名称，value为特性详情
     */
    Map<String, Object> getAllFeatureDetail();

}
