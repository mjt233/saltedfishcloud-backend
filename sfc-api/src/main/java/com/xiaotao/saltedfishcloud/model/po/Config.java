package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统配置项实体类
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Config {

    /**
     * @see SysCommonConfig
     */
    @Id
    private String itemKey;

    @Lob
    private String itemValue;
}

