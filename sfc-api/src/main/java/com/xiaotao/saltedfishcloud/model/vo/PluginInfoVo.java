package com.xiaotao.saltedfishcloud.model.vo;

import com.xiaotao.saltedfishcloud.model.PluginInfo;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PluginInfoVo extends PluginInfo {
    /**
     * 临时id
     */
    private Long tempId;
}
