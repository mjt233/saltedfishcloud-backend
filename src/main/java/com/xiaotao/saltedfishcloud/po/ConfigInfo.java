package com.xiaotao.saltedfishcloud.po;

import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigInfo {
    private ConfigName key;
    private String value;
}
