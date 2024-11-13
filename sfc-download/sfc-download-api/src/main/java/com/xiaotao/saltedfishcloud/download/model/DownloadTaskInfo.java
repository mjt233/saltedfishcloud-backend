package com.xiaotao.saltedfishcloud.download.model;

import com.sfc.task.model.AsyncTaskRecord;
import jakarta.persistence.*;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.validation.constraints.NotEmpty;
import java.util.Date;

@Setter
@Getter
@Entity
@Table(name = "download_task", indexes = {
        @Index(name = "idx_uid", columnList = "uid")
})
@EntityListeners(AuditingEntityListener.class)
public class DownloadTaskInfo {
    public enum State {
        WAITING, DOWNLOADING, FAILED, FINISH, CANCEL
    }

    @Id
    @UuidGenerator
    private String id;

    private long uid;

    @NotEmpty
    @Lob
    private String url;

    private String proxy;

    @Enumerated(EnumType.STRING)
    private State state = State.WAITING;

    @Lob
    private String message;

    private long loaded;

    private long size;

    @Transient
    private long speed;

    @Column(length = 1024)
    private String name;

    @Column(name = "save_path", length = 2048)
    @NotEmpty
    private String savePath;

    @Column(name = "created_at")
    @CreatedDate
    private Date createdAt;

    @Column(name = "finish_at")
    private Date finishAt;

    private long createdBy;

    @OneToOne
    @JoinColumn(name = "task_id", referencedColumnName = "id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @NotFound(action = NotFoundAction.IGNORE)
    private AsyncTaskRecord asyncTaskRecord;

}
