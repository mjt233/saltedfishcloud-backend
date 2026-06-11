package com.sfc.dm.model.po;

import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.enums.InvalidDataType;
import com.sfc.dm.enums.ProcessMethod;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 失效数据记录
 */
@Getter
@Setter
@Entity
@Table(indexes = {
        @Index(name = "idx_invalid_data_record_status", columnList = "status"),
        @Index(name = "idx_invalid_data_record_owner_uid", columnList = "ownerUid"),
        @Index(name = "idx_invalid_data_record_md5", columnList = "md5")
})
public class InvalidDataRecord extends AuditModel {
    /**
     * 失效数据类型
     */
    @Enumerated(EnumType.STRING)
    private InvalidDataType type;

    /**
     * 完整物理存储路径
     */
    private String storagePath;

    /**
     * 所属用户id（公共网盘为0）
     */
    private Long ownerUid;

    /**
     * 网盘路径（UNIQUE模式下为null）
     */
    private String diskPath;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 最后修改时间
     */
    private Date lastModified;

    /**
     * 是否为待识别的文件
     */
    private Boolean needIdentify;

    /**
     * 文件类型typeId（识别后填充）
     */
    private String fileType;

    /**
     * 元数据JSON（识别后填充）
     */
    @Lob
    private String metadata;

    /**
     * 状态
     */
    @Enumerated(EnumType.STRING)
    private InvalidDataStatus status;

    /**
     * 处理方式（仅COMPLETED状态有值）
     */
    @Enumerated(EnumType.STRING)
    private ProcessMethod processMethod;

    /**
     * 文件MD5
     */
    private String md5;
}
