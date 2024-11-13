package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.annotations.SnowFlakeId;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Data
@NoArgsConstructor
@Table(
        name = "collection_rec",
        indexes = {
                @Index(name = "cid_index", columnList = "cid")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class CollectionRecord {
    @Id
    @SnowFlakeId
    private Long id;
    private Long cid;
    private Long uid;

    private String filename;
    private Long size;
    private String md5;
    private String ip;

    @Column(insertable = false, updatable = false)
    private String username;

    public CollectionRecord(Long cid, Long uid, String filename, Long size, String md5, String ip) {
        this.cid = cid;
        this.uid = uid;
        this.filename = filename;
        this.size = size;
        this.md5 = md5;
        this.ip = ip;
    }

    @CreatedDate
    private Date createdAt;
}
