package com.sfc.team.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 团队详情视图：团队信息 + 成员列表 + 当前用户角色
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDetailVO {
    /**
     * 团队信息
     */
    private TeamVO team;

    /**
     * 成员与待注册成员列表
     */
    private TeamMemberListVO memberList;

    /**
     * 当前用户在团队中的角色（1=READ 2=WRITE 3=MANAGE 4=OWNER）
     */
    private Integer myRole;
}
