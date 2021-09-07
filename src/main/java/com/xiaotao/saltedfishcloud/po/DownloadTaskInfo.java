package com.xiaotao.saltedfishcloud.po;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.util.Date;

@Entity()
@Table(name = "download_task")
@EntityListeners(AuditingEntityListener.class)
public class DownloadTaskInfo {
    public enum State {
        WAITING, DOWNLOADING, FAILED, FINISH, CANCEL
    }
    @Id
    public String id;
    public int uid;

    @NotEmpty
    public String url;

    public String proxy;

    @Enumerated(EnumType.STRING)
    public State state = State.WAITING;
    public String message;
    public long loaded;
    public long size;
    @Transient
    public long speed;
    public String name;

    @Column(name = "save_path")
    @NotEmpty
    public String savePath;

    @Column(name = "created_at")
    public Date createdAt;
    @Column(name = "finish_at")
    public Date finishAt;

    public int createdBy;

}
