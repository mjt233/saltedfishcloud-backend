package com.sfc.cloud.data.model;

import com.sfc.cloud.data.id.SfcSnowFlakeIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 实体类基础模板。
 *
 * <p>提供统一的 {@code Long} 主键，主键值由 {@link SfcSnowFlakeIdGenerator}
 * 以雪花算法生成；列名固定为 {@code id}，与宿主既有数据库结构保持一致。</p>
 *
 * <p>迁移自宿主的 {@code com.xiaotao.saltedfishcloud.model.template.BaseModel}，
 * 仅替换主键生成注解的包路径，其余语义逐字保留。</p>
 *
 * @see SfcAuditModel
 */
@Getter
@Setter
@MappedSuperclass
public class SfcBaseModel implements Serializable {

    /**
     * 主键id，由雪花算法生成
     */
    @Id
    @SfcSnowFlakeIdGenerator
    @Column(name = "id")
    private Long id;
}
