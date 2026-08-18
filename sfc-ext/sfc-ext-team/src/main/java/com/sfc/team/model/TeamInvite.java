package com.sfc.team.model;

import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 团队邀请实体（表 sys_team_invite）。
 * <p>
 * 为系统中不存在的用户创建「待注册成员」时生成邀请记录；接受注册后回填 acceptedUid 并置为已接受。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "sys_team_invite", indexes = {
        @Index(name = "idx_invite_team", columnList = "teamId"),
        @Index(name = "idx_invite_token", columnList = "token", unique = true)
})
public class TeamInvite extends BaseModel {

    /**
     * 所属团队 id
     */
    private Long teamId;

    /**
     * 邀请人用户 id
     */
    private Long inviterUid;

    /**
     * 预填用户名（可为空，接受时可自行填写）
     */
    private String username;

    /**
     * 预填邮箱（可为空，接受后作为新用户邮箱）
     */
    private String email;

    /**
     * 接受后生效的角色：1=READ 2=WRITE 3=MANAGE（不含 OWNER）
     */
    private Integer role;

    /**
     * 邀请令牌（随机生成，单次使用）
     */
    @Column(unique = true)
    private String token;

    /**
     * 邀请状态：1=待接受 2=已接受 3=已撤销
     */
    @Builder.Default
    private Integer status = 1;

    /**
     * 过期时间
     */
    private Date expireAt;

    /**
     * 接受后回填的新用户 id
     */
    private Long acceptedUid;

    /**
     * 创建时间
     */
    @CreatedDate
    @Column(name = "create_at")
    private Date createAt;
}
