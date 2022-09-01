package com.xiaotao.saltedfishcloud.dao.mybatis;

import com.xiaotao.saltedfishcloud.service.config.SysConfigName;
import com.xiaotao.saltedfishcloud.model.Pair;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ConfigDao {

    @Insert("INSERT INTO config (`key`,`value`) VALUES (#{key}, #{value}) ON DUPLICATE KEY UPDATE `value`=#{value}")
    int setConfig(@Param("key") String key,@Param("value") String value);

    @Select("SELECT `value` FROM config WHERE `key` = #{key}")
    String getConfig(String key);


    /**
     * 读取一条配置
     * @param key   配置键名
     * @return      配置值
     */
    @Select("SELECT `value` FROM config WHERE `key` = #{key}")
    String getConfigure(String key);

    /**
     * 读取一条配置
     * @param key   配置键名
     * @param defaultValue 当配置项不存在时返回的默认值
     * @return      配置值
     */
    default String getConfigure(String key, String defaultValue) {
        String configure = getConfigure(key);
        if (configure != null) {
            return configure;
        } else {
            return defaultValue;
        }
    }

    /**
     * 读取所有配置选项
     * @return
     */
    @Select("SELECT `key`,`value` FROM config")
    List<Pair<String, String>> getAllConfig();

    /**
     * 设置一条配置信息
     * @param key       键
     * @param value     值
     */
    @Insert("INSERT INTO config (`key`,`value`) VALUES (#{key}, #{value}) ON DUPLICATE KEY UPDATE `value`=#{value}")
    int setConfigure(String key, String value);

    /**
     * 设置一条配置信息
     * @param key       键
     * @param value     值
     */
    default int setConfigure(String key, Enum<?> value) {
        return setConfigure(key, value.toString());
    }
}
