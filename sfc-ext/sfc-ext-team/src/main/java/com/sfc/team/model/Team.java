package com.sfc.team.model;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotBlank;

/**
 * 团队实体（表 sys_team）。
 * <p>
 * 团队 id 由基类经雪花算法生成，同时作为团队文件空间的 UID（teamUid = id）。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "sys_team", indexes = {
        @Index(name = "idx_team_owner", columnList = "ownerUid"),
        @Index(name = "idx_team_status", columnList = "status")
})
public class Team extends AuditModel {

    /**
     * 团队名称
     */
    @NotBlank
    @Length(max = 64)
    private String name;

    /**
     * 团队描述
     */
    @Length(max = 255)
    private String description;

    /**
     * 所有者用户 id（user 表）
     */
    private Long ownerUid;

    /**
     * 团队状态：1=正常 2=已解散
     */
    @Builder.Default
    private Integer status = 1;
}
