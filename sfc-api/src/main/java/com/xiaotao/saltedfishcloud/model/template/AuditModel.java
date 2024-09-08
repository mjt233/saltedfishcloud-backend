package com.xiaotao.saltedfishcloud.model.template;


import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.Column;
import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;
import java.util.Date;

@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
@Getter
@Setter
public class AuditModel extends BaseModel {
    /**
     * 数据所属人
     */
    @Column(name = "uid", columnDefinition = "BIGINT UNSIGNED COMMENT '数据所属人'")
    private Long uid;

    /**
     * 数据创建日期
     */
    @CreatedDate
    @Column(name = "create_at", columnDefinition = "DATETIME COMMENT '创建日期'")
    private Date createAt;

    /**
     * 数据更新日期
     */
    @LastModifiedDate
    @Column(name = "update_at", columnDefinition = "DATETIME COMMENT '修改日期'")
    private Date updateAt;
}
