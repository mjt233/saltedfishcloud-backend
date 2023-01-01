package com.xiaotao.saltedfishcloud.model.template;


import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.Inheritance;
import javax.persistence.MappedSuperclass;
import java.util.Date;

@EntityListeners(AuditingEntityListener.class)
@Inheritance
@MappedSuperclass
@Getter
@Setter
public class AuditModel extends BaseModel {
    private Long uid;

    @CreatedDate
    private Date createAt;

    @LastModifiedDate
    private Date updateAt;
}
