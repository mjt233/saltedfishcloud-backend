package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.annotations.id.SnowFlakeIdGenerator;
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
    @SnowFlakeIdGenerator
    private Long id;

    /**
     * 文件收集任务id
     */
    private Long cid;

    /**
     * 文件提交人用户id
     */
    private Long uid;

    /**
     * 提交的文件名
     */
    private String filename;

    /**
     * 文件大小
     */
    private Long size;

    /**
     * 提交的文件md5哈希值
     */
    private String md5;

    /**
     * 提交人ip地址
     */
    private String ip;

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
