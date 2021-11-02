package com.xiaotao.saltedfishcloud.entity.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.Table;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "collection_rec")
@EntityListeners(AuditingEntityListener.class)
public class CollectionRecord {
    @EmbeddedId
    private CollectionRecordId id;

    private String filename;
    private Long size;
    private String md5;
}
