package com.xiaotao.saltedfishcloud.model.config;

import com.xiaotao.saltedfishcloud.annotations.*;
import com.xiaotao.saltedfishcloud.service.log.LogLevel;
import lombok.Data;

@Data
@ConfigPropertyEntity(
        prefix = "sys.log",
        defaultKeyNameStrategy = ConfigKeyNameStrategy.UNDER_SCORE_CASE,
        groups = {
                @ConfigPropertiesGroup(id = "base", name = "基本信息"),
                @ConfigPropertiesGroup(id = "storage", name = "日志存储配置")
        }
)
public class SysLogConfig {
    @ConfigProperty(
            inputType = "switch",
            title = "启用日志记录",
            defaultValue = "false",
            describe = "全局开关，若不启用则系统不会记录任何日志信息到单独的日志数据库"
    )
    private Boolean enableLog;

    @ConfigProperty(
            inputType = "switch",
            title = "启用系统日志自动采集",
            defaultValue = "false",
            describe = "常规通过logger输出的日志是否自动采集，并保存到单独的日志数据库"
    )
    private Boolean enableAutoLog;


    @ConfigProperty(
            inputType = "select",
            title = "自动记录的日志级别",
            defaultValue = "WARN",
            options = {
                @ConfigSelectOption(title = "TRACE", value = "TRACE"),
                @ConfigSelectOption(title = "DEBUG", value = "DEBUG"),
                @ConfigSelectOption(title = "INFO", value = "INFO"),
                @ConfigSelectOption(title = "WARN", value = "WARN"),
                @ConfigSelectOption(title = "ERROR", value = "ERROR")
            }
    )
    private LogLevel autoLogLevel;

    @ConfigProperty(
            inputType = "template",
            title = "主日志存储方式",
            template = "log-record-storage-selector",
            defaultValue = "Database",
            group = "storage"
    )
    private String mainLogRecordStorage;

    @ConfigProperty(
            inputType = "switch",
            title = "停用控制台日志输出",
            defaultValue = "false",
            describe = "停用后，系统初始化加载完成后将不再输出日志到控制台。（输出到文件不受影响）"
    )
    private Boolean disableConsoleOutput;
}
