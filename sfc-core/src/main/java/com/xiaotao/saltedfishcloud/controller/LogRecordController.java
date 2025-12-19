package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.constant.SysRole;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.NameValueType;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.LogRecordQueryParam;
import com.xiaotao.saltedfishcloud.model.param.RangeRequest;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.model.vo.LogRecordStatisticVO;
import com.xiaotao.saltedfishcloud.service.log.LogRecordManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Api(tags = "系统日志记录")
@RequestMapping("/api/logRecord")
public class LogRecordController {
    @Autowired
    private LogRecordManager logRecordManager;

    @RequestMapping("queryLog")
    @RolesAllowed(SysRole.ADMIN)
    @ApiOperation("查询日志")
    public JsonResult<CommonPageInfo<LogRecord>> queryLog(@RequestBody LogRecordQueryParam queryParam) {
        return JsonResultImpl.getInstance(logRecordManager.queryLog(queryParam));
    }

    @RequestMapping("queryLogStatistic")
    @RolesAllowed(SysRole.ADMIN)
    @ApiOperation("查询日志汇总统计数据")
    public JsonResult<List<LogRecordStatisticVO>> queryLogStatistic(@RequestBody(required = false) RangeRequest<Date> queryParam) {
        return JsonResultImpl.getInstance(logRecordManager.queryStatistic(queryParam));
    }

    @RequestMapping("listStorage")
    @RolesAllowed(SysRole.ADMIN)
    @ApiOperation("列出可用的日志存储器")
    public JsonResult<List<NameValueType<String>>> listStorage() {
        return JsonResultImpl.getInstance(logRecordManager.getAllStorage().stream()
                .map(e -> NameValueType.<String>builder().name(e.getName() + " - " + e.getClass().getSimpleName()).value(e.getName()).build())
                .collect(Collectors.toList())
        );
    }
}
