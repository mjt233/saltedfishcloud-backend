package com.xiaotao.saltedfishcloud.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志记录统计数据
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogRecordStatisticVO {
    /**
     * 日志类型
     */
    private String type;

    /**
     * 日志数量
     */
    private Long count;
}
