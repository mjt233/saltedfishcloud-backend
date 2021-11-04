package com.xiaotao.saltedfishcloud.entity.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.util.Date;

@Entity
@Data
@NoArgsConstructor
@Table(name = "collection_rec")
@EntityListeners(AuditingEntityListener.class)
public class CollectionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long cid;
    private Integer uid;

    private String filename;
    private Long size;
    private String md5;

    public CollectionRecord(Long cid, Integer uid, String filename, Long size, String md5) {
        this.cid = cid;
        this.uid = uid;
        this.filename = filename;
        this.size = size;
        this.md5 = md5;
    }

    @CreatedDate
    private Date createdAt;
}
