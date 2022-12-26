package com.xiaotao.saltedfishcloud.model.template;

import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.io.Serializable;

@Data
public class BaseModel {

    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "com.xiaotao.saltedfishcloud.utils.identifier.SnowFlakeIdGenerator")
    private Long id;
}
