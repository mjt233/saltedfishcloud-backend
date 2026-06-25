package com.sfc.dm.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 认领记录（仅UNIQUE模式）
 */
@Getter
@Setter
@Entity
@Table(indexes = {
        @Index(name = "idx_claim_record_invalid_data_id", columnList = "invalidDataId"),
        @Index(name = "idx_claim_record_uid", columnList = "uid")
})
public class ClaimRecord extends AuditModel {
    /**
     * 关联的失效数据记录ID
     */
    private Long invalidDataId;

    /**
     * 保存的目标网盘id（0=公共网盘，>0=对应用户id的私人网盘）
     */
    private Long targetUid;

    /**
     * 认领时填写的文件名
     */
    private String fileName;

    /**
     * 认领时填写的保存路径(不含文件名本身)
     */
    private String savePath;
}
