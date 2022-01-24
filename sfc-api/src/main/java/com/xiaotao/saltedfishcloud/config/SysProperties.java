package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.service.config.version.Version;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sys")
@Data
public class SysProperties {


    @Value("${app.version}")
    private Version version;


    // 注册邀请码
    @Value("${reg-code}")
    private String regCode;


    public void setVersion(String v) {
        version = Version.valueOf(v);
    }
}
