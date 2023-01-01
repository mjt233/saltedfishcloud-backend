package com.saltedfishcloud.ext.ve.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class EncodeConvertParam {

    /**
     * 转换规则
     */
    private List<EncodeConvertRule> rules;

    /**
     * 封装格式
     */
    private String format;
}
