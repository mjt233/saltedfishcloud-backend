package com.xiaotao.saltedfishcloud.model.template;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.MappedSuperclass;
import java.io.Serializable;

@Getter
@Setter
@Inheritance
@MappedSuperclass
public class BaseModel {

    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "com.xiaotao.saltedfishcloud.utils.identifier.SnowFlakeIdGenerator")
    private Long id;
}
