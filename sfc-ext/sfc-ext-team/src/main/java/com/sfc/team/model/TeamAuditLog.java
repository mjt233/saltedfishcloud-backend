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

/**
 * 团队操作审计日志实体（表 sys_team_audit_log）。
 * <p>
 * 记录团队成员/权限/邀请等关键变更；基类 uid 字段即操作人 id。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "sys_team_audit_log", indexes = {
        @Index(name = "idx_audit_team", columnList = "teamId")
})
public class TeamAuditLog extends AuditModel {

    /**
     * 所属团队 id
     */
    private Long teamId;

    /**
     * 操作类型：CREATE_TEAM/ADD_MEMBER/REMOVE_MEMBER/UPDATE_ROLE/CREATE_INVITE/REVOKE_INVITE/
     * REGENERATE_INVITE/ACCEPT_INVITE/TRANSFER_OWNER/DISSOLVE_TEAM/QUIT_TEAM
     */
    @Length(max = 32)
    private String action;

    /**
     * 操作对象（非团队 id 的目标标识）
     */
    @Length(max = 64)
    private String target;

    /**
     * 操作明细
     */
    @Length(max = 512)
    private String detail;
}
