package com.sfc.cloud.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统配置项键值对
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SfcConfigItem {

    /** 配置项名称（配置表中的键） */
    private String name;

    /** 配置项值（一律以字符串形式传输，由各端按配置定义自行转换类型） */
    private String value;

    /**
     * 快捷构造一个配置项。
     *
     * @param name  配置项名称
     * @param value 配置项值
     * @return 配置项实例
     */
    public static SfcConfigItem of(String name, String value) {
        return new SfcConfigItem(name, value);
    }
}
