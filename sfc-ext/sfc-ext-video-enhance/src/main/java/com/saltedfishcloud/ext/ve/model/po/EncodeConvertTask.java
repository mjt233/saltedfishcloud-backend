package com.saltedfishcloud.ext.ve.model.po;

import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

@Entity
@Table(indexes = {
        @Index(name = "idx_uid", columnList = "uid"),
        @Index(name = "idx_task_id", columnList = "task_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncodeConvertTask extends AuditModel {

    /**
     * 任务类型
     */
    private String type;

    /**
     * 任务JSON参数
     */
    @Lob
    private String params;

    @OneToOne
    @JoinColumn(name = "task_id", referencedColumnName = "id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @NotFound(action = NotFoundAction.IGNORE)
    private AsyncTaskRecord asyncTaskRecord;

    @Transient
    private ProgressRecord progress;
}
