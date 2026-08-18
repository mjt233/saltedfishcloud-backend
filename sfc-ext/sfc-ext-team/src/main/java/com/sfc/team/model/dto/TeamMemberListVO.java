package com.sfc.team.model.dto;

import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 团队成员列表视图：正常成员（分页） + 待注册成员（邀请列表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberListVO {
    /**
     * 正常成员分页列表
     */
    private CommonPageInfo<MemberVO> members;

    /**
     * 待注册成员（有效待接受邀请）列表
     */
    private List<InviteVO> invites;
}
