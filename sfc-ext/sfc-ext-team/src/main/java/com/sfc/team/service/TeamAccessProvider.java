package com.sfc.team.service;

import com.sfc.team.constant.TeamStatus;
import com.sfc.team.model.Team;
import com.sfc.team.model.TeamMember;
import com.sfc.team.repo.TeamMemberRepo;
import com.sfc.team.repo.TeamRepo;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.permission.UidAccessLevel;
import com.xiaotao.saltedfishcloud.service.permission.UidAccessProvider;
import com.xiaotao.saltedfishcloud.service.permission.UidAccessResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 团队空间 UID 访问权提供者。
 * <p>
 * 向核心 {@link UidAccessResolver} 声明：某用户是其成员时，对该团队 uid（团队 id）的访问级别；
 * 非成员或团队不存在/已解散返回 NONE。
 */
@Service
@RequiredArgsConstructor
public class TeamAccessProvider implements UidAccessProvider {

    /**
     * 团队仓库
     */
    private final TeamRepo teamRepo;

    /**
     * 团队成员仓库
     */
    private final TeamMemberRepo memberRepo;

    /**
     * {@inheritDoc}
     * <p>
     * 以 uid 查询团队（团队 id 即文件空间 uid），存在且为正常团队时按成员角色映射访问级别。
     */
    @Override
    public UidAccessLevel getAccessLevel(UserPrincipal user, long uid) {
        if (user == null || user.getId() == null) {
            return UidAccessLevel.NONE;
        }
        Team team = teamRepo.findById(uid).orElse(null);
        if (team == null || team.getStatus() == null || team.getStatus() != TeamStatus.TEAM_NORMAL) {
            return UidAccessLevel.NONE;
        }
        TeamMember member = memberRepo.findByTeamIdAndUidAndStatus(uid, user.getId(), TeamStatus.MEMBER_ACTIVE)
                .orElse(null);
        if (member == null || member.getRole() == null) {
            return UidAccessLevel.NONE;
        }
        com.sfc.team.constant.TeamRole role = com.sfc.team.constant.TeamRole.of(member.getRole());
        return role == null ? UidAccessLevel.NONE : role.toUidAccessLevel();
    }
}
