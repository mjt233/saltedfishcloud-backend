package com.xiaotao.saltedfishcloud.download.model;

import com.sfc.task.model.AsyncTaskRecord;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.Proxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.util.Date;

@Setter
@Getter
@Entity
@Proxy(lazy = false)
@Table(name = "download_task", indexes = {
        @Index(name = "idx_uid", columnList = "uid")
})
@EntityListeners(AuditingEntityListener.class)
@GenericGenerator(name = "jpa-uuid", strategy = "uuid")
public class DownloadTaskInfo {
    public enum State {
        WAITING, DOWNLOADING, FAILED, FINISH, CANCEL
    }

    @Id
    @GeneratedValue(generator = "jpa-uuid")
    private String id;

    private long uid;

    @NotEmpty
    private String url;

    private String proxy;

    @Enumerated(EnumType.STRING)
    private State state = State.WAITING;

    @Column(columnDefinition = "text")
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
