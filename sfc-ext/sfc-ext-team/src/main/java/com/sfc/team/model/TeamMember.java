package com.sfc.team.model;

import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;

/**
 * 团队成员实体（表 sys_team_member），仅记录已注册用户成员。
 * <p>
 * 待注册成员以 {@link TeamInvite}（状态=待接受）形式存在，与正常成员共同构成团队成员列表。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "sys_team_member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_team_member", columnNames = {"teamId", "uid"})
}, indexes = {
        @Index(name = "idx_member_uid", columnList = "uid")
})
public class TeamMember extends BaseModel {

    /**
     * 所属团队 id
     */
    private Long teamId;

    /**
     * 成员用户 id（user 表）
     */
    private Long uid;

    /**
     * 成员角色：1=READ 2=WRITE 3=MANAGE 4=OWNER
     */
    private Integer role;

    /**
     * 成员状态：1=正常 2=已移除
     */
    @Builder.Default
    private Integer status = 1;

    /**
     * 入团时间
     */
    @CreatedDate
    @Column(name = "create_at")
    private Date createAt;
}
