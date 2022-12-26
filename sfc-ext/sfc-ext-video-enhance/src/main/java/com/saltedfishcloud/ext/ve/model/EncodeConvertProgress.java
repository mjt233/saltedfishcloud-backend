package com.saltedfishcloud.ext.ve.model;

import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import lombok.Getter;
import lombok.Setter;

/**
 * 编码转换进度
 */
public class EncodeConvertProgress extends ProgressRecord {

    /**
     * 每秒钟处理的帧数
     */
    @Getter
    @Setter
    private Double fps;
}
