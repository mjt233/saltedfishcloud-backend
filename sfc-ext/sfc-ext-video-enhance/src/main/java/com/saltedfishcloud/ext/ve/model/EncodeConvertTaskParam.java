package com.saltedfishcloud.ext.ve.model;

import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编码转换【任务参数】
 */
@Data
public class EncodeConvertTaskParam {
    /**
     * 待转换资源
     */
    private ResourceRequest source;

    /**
     * 转换产出目标存放点
     */
    private ResourceRequest target;

    /**
     * 编码【转换规则】参数
     */
    private EncodeConvertParam encodeConvertParam;

    /**
     * 新文件存在同名时，是否覆盖
     */
    private Boolean isOverwrite;
}
