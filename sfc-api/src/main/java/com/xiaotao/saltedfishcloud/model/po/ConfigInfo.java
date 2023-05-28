package com.xiaotao.saltedfishcloud.model.po;

import com.sfc.constant.SysConfigName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigInfo {
    private SysConfigName key;
    private String value;
}
