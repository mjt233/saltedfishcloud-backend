package com.xiaotao.saltedfishcloud.model.template;


import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.EntityListeners;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@EntityListeners(AuditingEntityListener.class)
public class AuditModel extends BaseModel {
    private Long uid;

    @CreatedDate
    private Date createAt;

    @LastModifiedDate
    private Date updateAt;
}
