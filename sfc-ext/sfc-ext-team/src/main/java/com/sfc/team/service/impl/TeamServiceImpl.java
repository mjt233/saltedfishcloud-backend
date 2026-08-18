package com.sfc.team.service.impl;

import com.sfc.team.constant.TeamRole;
import com.sfc.team.constant.TeamStatus;
import com.sfc.team.model.Team;
import com.sfc.team.model.TeamAuditLog;
import com.sfc.team.model.TeamMember;
import com.sfc.team.model.TeamProperty;
import com.sfc.team.model.dto.AddMemberRequest;
import com.sfc.team.model.dto.CreateTeamRequest;
import com.sfc.team.model.dto.InviteVO;
import com.sfc.team.model.dto.MemberVO;
import com.sfc.team.model.dto.RemoveMemberRequest;
import com.sfc.team.model.dto.TeamDetailVO;
import com.sfc.team.model.dto.TeamIdRequest;
import com.sfc.team.model.dto.TeamMemberListVO;
import com.sfc.team.model.dto.TeamVO;
import com.sfc.team.model.dto.TransferOwnerRequest;
import com.sfc.team.model.dto.UpdateMemberRoleRequest;
import com.sfc.team.model.dto.UpdateTeamInfoRequest;
import com.sfc.team.model.dto.UserSearchResult;
import com.sfc.team.repo.TeamAuditLogRepo;
import com.sfc.team.repo.TeamInviteRepo;
import com.sfc.team.repo.TeamMemberRepo;
import com.sfc.team.repo.TeamRepo;
import com.sfc.team.service.TeamService;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 团队管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    /**
     * 团队仓库
     */
    private final TeamRepo teamRepo;

    /**
     * 团队成员仓库
     */
    private final TeamMemberRepo memberRepo;

    /**
     * 团队邀请仓库（撤销后成员列表过滤等场景复用）
     */
    private final TeamInviteRepo inviteRepo;

    /**
     * 团队审计日志仓库
     */
    private final TeamAuditLogRepo auditLogRepo;

    /**
     * 用户服务（搜索/查询用户）
     */
    private final UserService userService;

    /**
     * 团队配置
     */
    private final TeamProperty teamProperty;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TeamVO createTeam(UserPrincipal user, CreateTeamRequest req) {
        if (teamProperty.getMaxTeamsPerUser() != null) {
            long count = teamRepo.countByOwnerUidAndStatus(user.getId(), TeamStatus.TEAM_NORMAL);
            if (count >= teamProperty.getMaxTeamsPerUser()) {
                throw new JsonException(400, "达到团队创建数量上限：" + teamProperty.getMaxTeamsPerUser());
            }
        }
        Team team = Team.builder()
                .name(req.getName())
                .description(req.getDescription())
                .ownerUid(user.getId())
                .status(TeamStatus.TEAM_NORMAL)
                .build();
        team.setUid(user.getId());
        team = teamRepo.save(team);

        TeamMember owner = TeamMember.builder()
                .teamId(team.getId())
                .uid(user.getId())
                .role(TeamRole.OWNER.getCode())
                .status(TeamStatus.MEMBER_ACTIVE)
                .build();
        memberRepo.save(owner);
        logAction(team.getId(), user.getId(), "CREATE_TEAM", null, "创建团队：" + team.getName());
        return toVO(team, TeamRole.OWNER, 1L);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TeamVO updateInfo(UserPrincipal user, UpdateTeamInfoRequest req) {
        Team team = getNormalTeam(req.getTeamId());
        TeamMember member = requireRole(user, req.getTeamId(), TeamRole.MANAGE);
        if (StringUtils.hasText(req.getName())) {
            team.setName(req.getName());
        }
        if (req.getDescription() != null) {
            team.setDescription(req.getDescription());
        }
        team = teamRepo.save(team);
        logAction(team.getId(), user.getId(), "UPDATE_TEAM_INFO", null, "修改团队信息");
        return toVO(team, TeamRole.of(member.getRole()), memberRepo.countByTeamIdAndStatus(team.getId(), TeamStatus.MEMBER_ACTIVE));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteTeam(UserPrincipal user, TeamIdRequest req) {
        Team team = getNormalTeam(req.getTeamId());
        boolean isOwner = user.isAdmin() || isOwner(team, user.getId());
        if (!isOwner) {
            throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        }
        // 团队文件删除由前端在确认后经核心文件接口执行；此处置团队/成员/邀请状态并留存审计
        team.setStatus(TeamStatus.TEAM_DISSOLVED);
        teamRepo.save(team);
        List<TeamMember> members = memberRepo.findByTeamIdAndStatus(team.getId(), TeamStatus.MEMBER_ACTIVE, PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        members.forEach(m -> m.setStatus(TeamStatus.MEMBER_REMOVED));
        memberRepo.saveAll(members);
        List<com.sfc.team.model.TeamInvite> invites = inviteRepo.findByTeamIdAndStatus(team.getId(), TeamStatus.INVITE_PENDING);
        invites.forEach(i -> i.setStatus(TeamStatus.INVITE_REVOKED));
        inviteRepo.saveAll(invites);
        logAction(team.getId(), user.getId(), "DISSOLVE_TEAM", null, "解散团队");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TeamVO> listMyTeams(UserPrincipal user, boolean all) {
        List<TeamMember> members;
        if (all && user.isAdmin()) {
            members = null;
        } else {
            members = memberRepo.findByUidAndStatus(user.getId(), TeamStatus.MEMBER_ACTIVE);
        }
        List<Team> teams;
        if (members == null) {
            teams = teamRepo.findAll();
        } else {
            List<Long> teamIds = members.stream().map(TeamMember::getTeamId).distinct().toList();
            if (teamIds.isEmpty()) {
                return Collections.emptyList();
            }
            teams = teamRepo.findAllById(teamIds);
        }
        teams = teams.stream().filter(t -> t.getStatus() == TeamStatus.TEAM_NORMAL).toList();
        if (teams.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        List<TeamMember> allMembers = memberRepo.findByTeamIdInAndStatus(teamIds, TeamStatus.MEMBER_ACTIVE);
        Map<Long, Long> countMap = allMembers.stream().collect(Collectors.groupingBy(TeamMember::getTeamId, Collectors.counting()));
        Map<Long, TeamRole> myRoleMap = members == null ? Collections.emptyMap()
                : members.stream().collect(Collectors.toMap(TeamMember::getTeamId, m -> TeamRole.of(m.getRole()), (a, _) -> a));
        Map<Long, Team> teamMap = teams.stream().collect(Collectors.toMap(Team::getId, Function.identity()));
        List<TeamVO> result = new ArrayList<>();
        for (Long teamId : teamIds) {
            Team team = teamMap.get(teamId);
            if (team == null) {
                continue;
            }
            TeamRole role = myRoleMap.get(teamId);
            if (role == null) {
                // 管理员查全部时角色为空，标记为 MANAGE 以允许管理操作
                role = user.isAdmin() ? TeamRole.MANAGE : null;
            }
            result.add(toVO(team, role, countMap.getOrDefault(teamId, 0L)));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TeamDetailVO getDetail(UserPrincipal user, Long teamId, int page, int size) {
        TeamMember member = requireRole(user, teamId, TeamRole.READ);
        Team team = getNormalTeam(teamId);
        TeamMemberListVO list = memberList(user, teamId, page, size);
        return TeamDetailVO.builder()
                .team(toVO(team, TeamRole.of(member.getRole()), memberRepo.countByTeamIdAndStatus(teamId, TeamStatus.MEMBER_ACTIVE)))
                .memberList(list)
                .myRole(member.getRole())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TeamMemberListVO memberList(UserPrincipal user, Long teamId, int page, int size) {
        requireRole(user, teamId, TeamRole.MANAGE);
        return buildMemberList(teamId, page, size);
    }

    /**
     * 构建成员列表（正常成员分页 + 待注册成员）
     *
     * @param teamId 团队 id
     * @param page   页码
     * @param size   每页大小
     * @return 成员列表视图
     */
    private TeamMemberListVO buildMemberList(Long teamId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
        Page<TeamMember> memberPage = memberRepo.findByTeamIdAndStatus(teamId, TeamStatus.MEMBER_ACTIVE, pageable);
        List<Long> uids = memberPage.getContent().stream().map(TeamMember::getUid).toList();
        Map<Long, User> userMap = uids.isEmpty() ? Collections.emptyMap()
                : userService.findBaseInfoByIds(uids).stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, _) -> a));
        List<MemberVO> memberVOs = memberPage.getContent().stream().map(m -> {
            User u = userMap.get(m.getUid());
            return MemberVO.builder()
                    .uid(m.getUid())
                    .username(u == null ? null : u.getUser())
                    .email(u == null ? null : u.getEmail())
                    .role(m.getRole())
                    .joinAt(m.getCreateAt() == null ? null : m.getCreateAt().getTime())
                    .build();
        }).toList();
        CommonPageInfo<MemberVO> pageInfo = new CommonPageInfo<MemberVO>()
                .setContent(memberVOs)
                .setTotalCount(memberPage.getTotalElements())
                .setTotalPage(memberPage.getTotalPages());

        long now = System.currentTimeMillis();
        List<InviteVO> inviteVOs = inviteRepo.findByTeamIdAndStatus(teamId, TeamStatus.INVITE_PENDING)
                .stream()
                .map(i -> InviteVO.builder()
                        .inviteId(i.getId())
                        .username(i.getUsername())
                        .email(i.getEmail())
                        .role(i.getRole())
                        .token(i.getToken())
                        .expireAt(i.getExpireAt() == null ? null : i.getExpireAt().getTime())
                        .createAt(i.getCreateAt() == null ? null : i.getCreateAt().getTime())
                        .expired(i.getExpireAt() != null && i.getExpireAt().getTime() < now)
                        .status(i.getStatus())
                        .build())
                .toList();
        return TeamMemberListVO.builder().members(pageInfo).invites(inviteVOs).build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void addMember(UserPrincipal user, AddMemberRequest req) {
        requireRole(user, req.getTeamId(), TeamRole.MANAGE);
        TeamRole role = requireNotOwnerRole(req.getRole());
        User target = userService.getUserById(req.getUid());
        if (target == null) {
            throw new JsonException(400, "目标用户不存在");
        }
        if (memberRepo.findByTeamIdAndUid(req.getTeamId(), req.getUid()).isPresent()) {
            throw new JsonException(400, "该用户已在团队中");
        }
        TeamMember member = TeamMember.builder()
                .teamId(req.getTeamId())
                .uid(req.getUid())
                .role(role.getCode())
                .status(TeamStatus.MEMBER_ACTIVE)
                .build();
        memberRepo.save(member);
        logAction(req.getTeamId(), user.getId(), "ADD_MEMBER", req.getUid().toString(), "添加成员：" + target.getUser());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void removeMember(UserPrincipal user, RemoveMemberRequest req) {
        requireRole(user, req.getTeamId(), TeamRole.MANAGE);
        TeamMember member = memberRepo.findByTeamIdAndUid(req.getTeamId(), req.getUid())
                .orElseThrow(() -> new JsonException(400, "成员不存在"));
        if (member.getRole() != null && member.getRole() == TeamRole.OWNER.getCode()) {
            throw new JsonException(400, "不能移除团队所有者");
        }
        member.setStatus(TeamStatus.MEMBER_REMOVED);
        memberRepo.save(member);
        logAction(req.getTeamId(), user.getId(), "REMOVE_MEMBER", req.getUid().toString(), "移除成员");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateMemberRole(UserPrincipal user, UpdateMemberRoleRequest req) {
        requireRole(user, req.getTeamId(), TeamRole.MANAGE);
        TeamRole newRole = requireNotOwnerRole(req.getRole());
        TeamMember member = memberRepo.findByTeamIdAndUidAndStatus(req.getTeamId(), req.getUid(), TeamStatus.MEMBER_ACTIVE)
                .orElseThrow(() -> new JsonException(400, "成员不存在"));
        if (member.getRole() != null && member.getRole() == TeamRole.OWNER.getCode()) {
            throw new JsonException(400, "不能修改团队所有者的角色");
        }
        member.setRole(newRole.getCode());
        memberRepo.save(member);
        logAction(req.getTeamId(), user.getId(), "UPDATE_ROLE", req.getUid().toString(),
                "角色变更为：" + newRole.name());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void quit(UserPrincipal user, TeamIdRequest req) {
        // 正常成员才可退出；所有者需先转移所有权
        TeamMember active = memberRepo.findByTeamIdAndUidAndStatus(req.getTeamId(), user.getId(), TeamStatus.MEMBER_ACTIVE)
                .orElseThrow(() -> new JsonException(400, "你不在该团队中"));
        if (active.getRole() != null && active.getRole() == TeamRole.OWNER.getCode()) {
            throw new JsonException(400, "所有者需先转移所有权才能退出团队");
        }
        active.setStatus(TeamStatus.MEMBER_REMOVED);
        memberRepo.save(active);
        logAction(req.getTeamId(), user.getId(), "QUIT_TEAM", user.getId().toString(), "退出团队");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void transferOwner(UserPrincipal user, TransferOwnerRequest req) {
        Team team = getNormalTeam(req.getTeamId());
        TeamMember owner = requireRole(user, req.getTeamId(), TeamRole.OWNER);
        if (req.getTargetUid().equals(user.getId())) {
            throw new JsonException(400, "不能将所有权转移给自己");
        }
        TeamMember target = memberRepo.findByTeamIdAndUidAndStatus(req.getTeamId(), req.getTargetUid(), TeamStatus.MEMBER_ACTIVE)
                .orElseThrow(() -> new JsonException(400, "目标用户不是正常成员"));
        // 管理员操作时 owner 可能为合成记录，仅当存在真实成员记录时才降级
        if (owner.getId() != null) {
            owner.setRole(TeamRole.MANAGE.getCode());
            memberRepo.save(owner);
        }
        target.setRole(TeamRole.OWNER.getCode());
        memberRepo.save(target);
        team.setOwnerUid(req.getTargetUid());
        teamRepo.save(team);
        logAction(team.getId(), user.getId(), "TRANSFER_OWNER", req.getTargetUid().toString(), "转移所有者");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UserSearchResult> searchUsers(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        CommonPageInfo<User> pageInfo = userService.searchUsers(keyword.trim(),
                new com.xiaotao.saltedfishcloud.model.param.PageableRequest(20, 0));
        return pageInfo.getContent().stream()
                .map(u -> UserSearchResult.builder()
                        .uid(u.getId())
                        .username(u.getUser())
                        .email(u.getEmail())
                        .build())
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Long, String> fileOwnerInfo(List<Long> uids) {
        if (uids == null || uids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> result = new HashMap<>();
        teamRepo.findAllById(uids).forEach(t -> result.put(t.getId(), t.getName()));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TeamMember requireRole(UserPrincipal user, Long teamId, TeamRole minRole) {
        TeamMember member = memberRepo.findByTeamIdAndUidAndStatus(teamId, user.getId(), TeamStatus.MEMBER_ACTIVE)
                .orElse(null);
        TeamRole role = member == null ? null : TeamRole.of(member.getRole());
        if (user.isAdmin()) {
            // 系统管理员拥有全部团队的管理权限；非成员时返回合成 OWNER 记录避免调用方空指针
            if (member == null) {
                return TeamMember.builder()
                        .teamId(teamId)
                        .uid(user.getId())
                        .role(TeamRole.OWNER.getCode())
                        .status(TeamStatus.MEMBER_ACTIVE)
                        .build();
            }
            return member;
        }
        boolean pass = role != null && role.getCode() >= minRole.getCode()
                && (minRole != TeamRole.OWNER || role == TeamRole.OWNER);
        if (!pass) {
            throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        }
        return member;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TeamRole getRole(UserPrincipal user, Long teamId) {
        TeamMember member = memberRepo.findByTeamIdAndUidAndStatus(teamId, user.getId(), TeamStatus.MEMBER_ACTIVE).orElse(null);
        return member == null ? null : TeamRole.of(member.getRole());
    }

    /**
     * 查询正常状态的团队，不存在或已解散时抛异常
     *
     * @param teamId 团队 id
     * @return 团队
     */
    private Team getNormalTeam(Long teamId) {
        return teamRepo.findByIdAndStatus(teamId, TeamStatus.TEAM_NORMAL)
                .orElseThrow(() -> new JsonException(400, "团队不存在或已解散"));
    }

    /**
     * 判断用户是否为团队所有者
     *
     * @param team 团队
     * @param uid  用户 id
     * @return 是否所有者
     */
    private boolean isOwner(Team team, Long uid) {
        return team.getOwnerUid() != null && team.getOwnerUid().equals(uid);
    }

    /**
     * 校验角色不能是 OWNER，并返回角色
     *
     * @param code 角色数值
     * @return 角色
     */
    private TeamRole requireNotOwnerRole(Integer code) {
        TeamRole role = TeamRole.of(code);
        if (role == null || role == TeamRole.OWNER) {
            throw new JsonException(400, "角色只能为 READ、WRITE 或 MANAGE");
        }
        return role;
    }

    /**
     * 写入审计日志
     *
     * @param teamId 团队 id
     * @param operatorUid 操作人
     * @param action      操作类型
     * @param target      操作对象
     * @param detail      明细
     */
    private void logAction(Long teamId, Long operatorUid, String action, String target, String detail) {
        TeamAuditLog log = TeamAuditLog.builder()
                .teamId(teamId)
                .action(action)
                .target(target)
                .detail(detail)
                .build();
        log.setUid(operatorUid);
        auditLogRepo.save(log);
    }

    /**
     * 转换为团队视图对象
     *
     * @param team        团队
     * @param role        我的角色
     * @param memberCount 成员数
     * @return 视图对象
     */
    private TeamVO toVO(Team team, TeamRole role, Long memberCount) {
        return TeamVO.builder()
                .id(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .ownerUid(team.getOwnerUid())
                .myRole(role == null ? null : role.getCode())
                .memberCount(memberCount)
                .build();
    }
}
