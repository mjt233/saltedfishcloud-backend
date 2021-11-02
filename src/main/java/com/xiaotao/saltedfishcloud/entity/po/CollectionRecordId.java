package com.xiaotao.saltedfishcloud.entity.po;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@Embeddable
public class CollectionRecordId implements Serializable {
    private static final long serialVersionUID = 123L;
    private String cid;
    private Integer uid;

    public CollectionRecordId(String cid, Integer uid) {
        this.cid = cid;
        this.uid = uid;
    }

    @CreatedDate
    private Date createdAt;
}
