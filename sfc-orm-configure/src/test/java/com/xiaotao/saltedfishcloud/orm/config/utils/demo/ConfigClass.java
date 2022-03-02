package com.xiaotao.saltedfishcloud.orm.config.utils.demo;

import com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigEntity;
import lombok.Data;

@ConfigEntity("sys")
@Data
public class ConfigClass {
    private SomeProperties props;
    private String name;

    @Data
    @ConfigEntity("props")
    public static class SomeProperties {
        private String name;
        private String city;
    }
}
