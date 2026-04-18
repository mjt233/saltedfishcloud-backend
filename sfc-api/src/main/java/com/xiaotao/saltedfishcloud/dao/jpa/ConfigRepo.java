package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.Config;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 系统配置项 JPA Repository
 */
public interface ConfigRepo extends JpaRepository<Config, String> {

    /**
     * 读取一条配置
     * @param key 配置键名
     * @return 配置值
     */
    default String getConfig(String key) {
        return findById(key).map(Config::getItemKey).orElse(null);
    }

    /**
     * 读取一条配置（别名）
     * @param key 配置键名
     * @return 配置值
     */
    default String getConfigure(String key) {
        return getConfig(key);
    }

    /**
     * 读取所有配置选项
     * @return 所有配置项列表
     */
    default List<Config> getAllConfig() {
        return findAll();
    }

    /**
     * 设置一条配置信息（upsert）
     * @param key   键
     * @param value 值
     */
    default void setConfig(@Param("key") String key, @Param("value") String value) {
        Config config = new Config(key, value);
        saveAndFlush(config);
    }

    /**
     * 设置一条配置信息（别名）
     * @param key   键
     * @param value 值
     */
    default void setConfigure(String key, String value) {
        setConfig(key, value);
    }

    /**
     * 按键列表（IN 查询）列出配置
     * @param keys 键列表
     * @return 匹配的配置项列表
     */
    List<Config> findByItemKeyIn(Collection<String> keys);
}



