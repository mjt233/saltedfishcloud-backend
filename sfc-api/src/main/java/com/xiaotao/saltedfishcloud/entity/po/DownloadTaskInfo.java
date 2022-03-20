package com.xiaotao.saltedfishcloud.entity.po;

import lombok.Data;
import org.hibernate.annotations.Proxy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.util.Date;

@Data
@Entity
@Proxy(lazy = false)
@Table(name = "download_task")
@EntityListeners(AuditingEntityListener.class)
public class DownloadTaskInfo {
    public enum State {
        WAITING, DOWNLOADING, FAILED, FINISH, CANCEL
    }

    @Id
    private String id;

    private int uid;

    @NotEmpty
    private String url;

    private String proxy;

    @Enumerated(EnumType.STRING)
    private State state = State.WAITING;

    private String message;

    private long loaded;

    private long size;

    @Transient
    private long speed;

    private String name;

    @Column(name = "save_path")
    @NotEmpty
    private String savePath;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "finish_at")
    private Date finishAt;

    private int createdBy;

}
