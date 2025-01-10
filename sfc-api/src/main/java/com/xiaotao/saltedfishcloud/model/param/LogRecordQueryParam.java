package com.xiaotao.saltedfishcloud.model.param;

import com.xiaotao.saltedfishcloud.service.log.LogLevel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 日志记录查询参数
 */
@Data
@ApiModel("日志记录查询参数")
public class LogRecordQueryParam {
    @ApiModelProperty("日志类型")
    private List<String> type;

    @ApiModelProperty("指定主机")
    private String host;

    @ApiModelProperty("指定进程id")
    private Long pid;

    @ApiModelProperty("日志级别")
    private List<LogLevel> level;

    @ApiModelProperty("产生该日志的操作用户id")
    private Long uid;

    @ApiModelProperty("数据日期请求")
    private RangeRequest<Date> dateRange;

    @ApiModelProperty("分页参数")
    private PageableRequest pageableRequest;
}
