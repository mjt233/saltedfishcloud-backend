package com.saltedfishcloud.ext.ve.model;

import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编码转换任务
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class EncodeConvertTaskParam extends EncodeConvertParam {
    /**
     * 待转换资源
     */
    private ResourceRequest source;

    /**
     * 转换产出目标存放点
     */
    private ResourceRequest target;
}
