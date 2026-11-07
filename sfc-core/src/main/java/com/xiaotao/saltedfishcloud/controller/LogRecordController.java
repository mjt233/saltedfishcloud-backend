package com.xiaotao.saltedfishcloud.controller;

import com.sfc.common.constant.SfcSysRole;
import com.sfc.common.model.SfcPageInfo;
import com.xiaotao.saltedfishcloud.model.NameValueType;
import com.sfc.common.model.json.SfcJsonResult;
import com.xiaotao.saltedfishcloud.model.param.LogRecordQueryParam;
import com.xiaotao.saltedfishcloud.model.param.RangeRequest;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.model.vo.LogRecordStatisticVO;
import com.xiaotao.saltedfishcloud.service.log.LogRecordManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Tag(name = "系统日志记录")
@RequestMapping("/api/logRecord")
public class LogRecordController {
    @Autowired
    private LogRecordManager logRecordManager;

    @RequestMapping("queryLog")
    @RolesAllowed(SfcSysRole.ADMIN)
    @Operation(summary = "查询日志")
    public SfcJsonResult<SfcPageInfo<LogRecord>> queryLog(@RequestBody LogRecordQueryParam queryParam) {
        return SfcJsonResult.getInstance(logRecordManager.queryLog(queryParam));
    }

    @RequestMapping("queryLogStatistic")
    @RolesAllowed(SfcSysRole.ADMIN)
    @Operation(summary = "查询日志汇总统计数据")
    public SfcJsonResult<List<LogRecordStatisticVO>> queryLogStatistic(@RequestBody(required = false) RangeRequest<Date> queryParam) {
        return SfcJsonResult.getInstance(logRecordManager.queryStatistic(queryParam));
    }

    @RequestMapping("listStorage")
    @RolesAllowed(SfcSysRole.ADMIN)
    @Operation(summary = "列出可用的日志存储器")
    public SfcJsonResult<List<NameValueType<String>>> listStorage() {
        return SfcJsonResult.getInstance(logRecordManager.getAllStorage().stream()
                .map(e -> NameValueType.<String>builder().name(e.getName() + " - " + e.getClass().getSimpleName()).value(e.getName()).build())
                .collect(Collectors.toList())
        );
    }
}
