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

        /**
         * 注册邀请码
         */
        private String regCode = "114514";
    }

    @Data
    public static class Store {

        /**
         * 存储服务类型，可选hdfs或local
         */
        private String type = "local";
    }


    public void setVersion(String v) {
        version = Version.valueOf(v);
    }
}
