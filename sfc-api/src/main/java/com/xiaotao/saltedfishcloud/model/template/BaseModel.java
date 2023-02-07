package com.xiaotao.saltedfishcloud.model.template;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Proxy;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

@Getter
@Setter
@MappedSuperclass
@Proxy(lazy = false)
public class BaseModel {

    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "com.xiaotao.saltedfishcloud.utils.identifier.SnowFlakeIdGenerator")
    private Long id;
}
