package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.service.config.version.Version;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
@ConfigurationProperties(prefix = "sys")
@Data
public class SysProperties {


    @Value("${app.version}")
    private Version version;
    private Common common;
    private Store store;

    @Data
    public static class Common {
        private String regCode;
    }

    @Data
    public static class Store {
        private String type;
    }


    public void setVersion(String v) {
        version = Version.valueOf(v);
    }
}
