package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.enums.ConfigName;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

public interface ConfigDao {
    /**
     * 读取一条配置
     * @param key   配置键名
     * @return      配置值
     */
    @Select("SELECT `value` FROM config WHERE `key` = #{key}")
    String getConfigure(ConfigName key);


    /**
     * 设置一条配置信息
     * @param key       键
     * @param value     值
     */
    @Insert("INSERT INTO config (`key`,`value`) VALUES (#{key}, #{value}) ON DUPLICATE KEY UPDATE `value`=#{value}")
    int setConfigure(ConfigName key, String value);
}
