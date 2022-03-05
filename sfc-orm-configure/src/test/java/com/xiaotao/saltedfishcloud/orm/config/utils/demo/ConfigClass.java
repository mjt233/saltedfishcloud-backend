package com.xiaotao.saltedfishcloud.orm.config.utils.demo;

import com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigEntity;
import com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigKey;
import com.xiaotao.saltedfishcloud.orm.config.enums.EntityKeyType;
import lombok.Data;

@ConfigEntity(value = "sys", keyType = EntityKeyType.NOT_ALL)
@Data
public class ConfigClass {

    @ConfigKey
    private SomeProperties props;
    private String name;
    private Long longVal;
    private Integer intVal;
    private Boolean booleanVal;
    private Double doubleVal;
    private short shortVal;

    @Data
    @ConfigEntity("props")
    public static class SomeProperties {
        private String name;
        private String city;
    }
}
