package com.xiaotao.saltedfishcloud.model.config;

import com.xiaotao.saltedfishcloud.annotations.ConfigKeyNameStrategy;
import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigSelectOption;
import com.xiaotao.saltedfishcloud.service.log.LogLevel;
import lombok.Data;

@Data
@ConfigPropertyEntity(
        prefix = "sys.log",
        defaultKeyNameStrategy = ConfigKeyNameStrategy.UNDER_SCORE_CASE
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
}
