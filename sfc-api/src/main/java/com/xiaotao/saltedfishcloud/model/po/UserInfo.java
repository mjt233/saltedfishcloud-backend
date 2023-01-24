package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 给jpa连接用的用户表模型
 */
@Entity(name = "user")
@Table(name = "user")
@Getter
@Setter
public class UserInfo extends BaseModel {
    private String user;

    private Integer type;
}
