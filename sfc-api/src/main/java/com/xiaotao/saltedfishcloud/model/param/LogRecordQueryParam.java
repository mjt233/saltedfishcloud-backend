package com.xiaotao.saltedfishcloud.model.param;

import com.xiaotao.saltedfishcloud.service.log.LogLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 日志记录查询参数
 */
@Data
@Schema(name = "日志记录查询参数")
public class LogRecordQueryParam {
    @Schema(description = "日志类型")
    private List<String> type;

    @Schema(description = "指定主机")
    private String host;

    @Schema(description = "指定进程id")
    private Long pid;

    @Schema(description = "日志级别")
    private List<LogLevel> level;

    @Schema(description = "产生该日志的操作用户id")
    private Long uid;

    @Schema(description = "数据日期请求")
    private RangeRequest<Date> dateRange;

    @Schema(description = "分页参数")
    private PageableRequest pageableRequest;
}
