package com.sfc.dm.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 文件识别请求参数
 */
@Getter
@Setter
public class IdentifyParam {

    /**
     * 指定需要识别的失效数据ID列表。<br>
     * 未指定或为空时，默认处理所有状态为待处理且需要识别的失效记录。
     */
    private List<Long> ids;

    /**
     * 是否重新识别。<br>
     * 为 true 时，即使失效记录无需识别（needIdentify = false）也执行重新识别，覆盖原有识别结果。
     */
    private Boolean reIdentify;
}
