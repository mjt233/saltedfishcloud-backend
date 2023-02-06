package com.saltedfishcloud.ext.ve.model.po;

import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import javax.persistence.*;

@Entity
@Table(name = "encode_convert_task")
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
    private String params;

    @OneToOne
    @JoinColumn(name = "task_id", referencedColumnName = "id")
    @NotFound(action = NotFoundAction.IGNORE)
    private AsyncTaskRecord asyncTaskRecord;

    @Transient
    private ProgressRecord progress;
}
