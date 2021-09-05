package com.xiaotao.saltedfishcloud.po;

import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.util.Date;

@Entity()
@Table(name = "download_task")
@EntityListeners(AuditingEntityListener.class)
public class DownloadTaskInfo {

    @Id
    public String id;
    public int uid;
    public String url;

    public String proxy;
    public String state = "waiting";

    @CreatedDate
    @Column(name = "created_at")
    public Date createdAt;

    @PrePersist
    protected void init() {
        if (createdAt == null) {
            createdAt = new Date();
        }
        if (id == null) {
            id = SecureUtils.getUUID();
        }
    }
}
