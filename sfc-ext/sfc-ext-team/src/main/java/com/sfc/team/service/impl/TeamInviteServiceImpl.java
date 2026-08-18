package com.sfc.team.service.impl;

import com.sfc.team.constant.TeamRole;
import com.sfc.team.constant.TeamStatus;
import com.sfc.team.model.Team;
import com.sfc.team.model.TeamAuditLog;
import com.sfc.team.model.TeamInvite;
import com.sfc.team.model.TeamMember;
import com.sfc.team.model.TeamProperty;
import com.sfc.team.model.dto.AcceptInviteRequest;
import com.sfc.team.model.dto.CreateInviteRequest;
import com.sfc.team.model.dto.InviteIdRequest;
import com.sfc.team.model.dto.InviteInfoVO;
import com.sfc.team.model.dto.InviteVO;
import com.sfc.team.repo.TeamAuditLogRepo;
import com.sfc.team.repo.TeamInviteRepo;
import com.sfc.team.repo.TeamMemberRepo;
import com.sfc.team.repo.TeamRepo;
import com.sfc.team.service.TeamInviteService;
import com.sfc.team.service.TeamService;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 团队邀请服务实现。
 */
@Service
@RequiredArgsConstructor
public class TeamInviteServiceImpl implements TeamInviteService {

    /**
     * 邀请仓库
     */
    private final TeamInviteRepo inviteRepo;

    /**
     * 团队仓库
     */
    private final TeamRepo teamRepo;

    /**
     * 成员仓库
     */
    private final TeamMemberRepo memberRepo;

    /**
     * 审计日志仓库
     */
    private final TeamAuditLogRepo auditLogRepo;

    /**
     * 团队服务（角色校验）
     */
    private final TeamService teamService;

    /**
     * 用户服务（接受邀请时创建账号）
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
    public InviteVO createInvite(UserPrincipal user, CreateInviteRequest req) {
        teamService.requireRole(user, req.getTeamId(), TeamRole.MANAGE);
        TeamRole role = requireNotOwnerRole(req.getRole());
        long expireDays = teamProperty.getInviteExpireDays() == null ? 7 : Math.max(teamProperty.getInviteExpireDays(), 1);
        TeamInvite invite = TeamInvite.builder()
                .teamId(req.getTeamId())
                .inviterUid(user.getId())
                .username(trimToNull(req.getUsername()))
                .email(trimToNull(req.getEmail()))
                .role(role.getCode())
                .token(generateToken())
                .status(TeamStatus.INVITE_PENDING)
                .expireAt(new Date(System.currentTimeMillis() + expireDays * 86400000L))
                .build();
        invite = inviteRepo.save(invite);
        logAction(req.getTeamId(), user.getId(), "CREATE_INVITE", invite.getId().toString(),
                "创建邀请，角色：" + role.name() + "，预填用户名：" + invite.getUsername());
        return toVO(invite);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InviteVO getInviteLink(UserPrincipal user, Long teamId, Long inviteId) {
        teamService.requireRole(user, teamId, com.sfc.team.constant.TeamRole.MANAGE);
        TeamInvite invite = getInvite(teamId, inviteId);
        return toVO(invite);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void revokeInvite(UserPrincipal user, InviteIdRequest req) {
        teamService.requireRole(user, req.getTeamId(), TeamRole.MANAGE);
        TeamInvite invite = getInvite(req.getTeamId(), req.getInviteId());
        invite.setStatus(TeamStatus.INVITE_REVOKED);
        inviteRepo.save(invite);
        logAction(req.getTeamId(), user.getId(), "REVOKE_INVITE", invite.getId().toString(), "撤销邀请");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public InviteVO regenerateInvite(UserPrincipal user, InviteIdRequest req) {
        teamService.requireRole(user, req.getTeamId(), TeamRole.MANAGE);
        TeamInvite invite = getInvite(req.getTeamId(), req.getInviteId());
        if (invite.getStatus() == TeamStatus.INVITE_ACCEPTED) {
            throw new JsonException(400, "该邀请已被接受，无法重新生成");
        }
        invite.setToken(generateToken());
        invite.setStatus(TeamStatus.INVITE_PENDING);
        invite.setExpireAt(new Date(System.currentTimeMillis()
                + (teamProperty.getInviteExpireDays() == null ? 7 : Math.max(teamProperty.getInviteExpireDays(), 1)) * 86400000L));
        invite = inviteRepo.save(invite);
        logAction(req.getTeamId(), user.getId(), "REGENERATE_INVITE", invite.getId().toString(), "重新生成邀请链接");
        return toVO(invite);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InviteInfoVO inviteInfo(String token) {
        TeamInvite invite = inviteRepo.findByToken(token)
                .orElseThrow(() -> new JsonException(400, "邀请链接无效"));
        Team team = teamRepo.findById(invite.getTeamId()).orElse(null);
        String inviterName = null;
        if (invite.getInviterUid() != null) {
            User inviter = userService.getUserById(invite.getInviterUid());
            inviterName = inviter == null ? null : inviter.getUser();
        }
        boolean valid = invite.getStatus() == TeamStatus.INVITE_PENDING && !isExpired(invite);
        return InviteInfoVO.builder()
                .teamName(team == null ? null : team.getName())
                .inviterName(inviterName)
                .username(invite.getUsername())
                .email(invite.getEmail())
                .expired(isExpired(invite))
                .valid(valid)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Long acceptInvite(AcceptInviteRequest req) {
        TeamInvite invite = inviteRepo.findByToken(req.getToken())
                .orElseThrow(() -> new JsonException(400, "邀请链接无效"));
        if (invite.getStatus() != TeamStatus.INVITE_PENDING) {
            throw new JsonException(400, "邀请已失效");
        }
        if (isExpired(invite)) {
            throw new JsonException(400, "邀请链接已过期");
        }
        Team team = teamRepo.findByIdAndStatus(invite.getTeamId(), TeamStatus.TEAM_NORMAL)
                .orElseThrow(() -> new JsonException(400, "团队不存在或已解散"));
        String username = StringUtils.hasText(invite.getUsername()) ? invite.getUsername() : req.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new JsonException(400, "用户名不能为空");
        }
        // 邀请令牌即验证凭据：直接建号（绕过公开注册开关与邮箱验证），账号角色在邀请创建时已确定
        userService.addUser(username, req.getPassword(), invite.getEmail(), UserConstants.TYPE_COMMON);
        User created = userService.getUserByAccount(username);
        if (created == null) {
            throw new JsonException(400, "账号创建失败");
        }
        TeamMember member = TeamMember.builder()
                .teamId(team.getId())
                .uid(created.getId())
                .role(invite.getRole())
                .status(TeamStatus.MEMBER_ACTIVE)
                .build();
        memberRepo.save(member);
        invite.setStatus(TeamStatus.INVITE_ACCEPTED);
        invite.setAcceptedUid(created.getId());
        inviteRepo.save(invite);
        logAction(team.getId(), created.getId(), "ACCEPT_INVITE", invite.getId().toString(), "接受邀请并注册账号");
        return created.getId();
    }

    /**
     * 生成邀请令牌（随机 UUID，去除连字符）
     *
     * @return 令牌
     */
    private String generateToken() {
        return IdUtil.getUUID().replace("-", "");
    }

    /**
     * 查询团队下的邀请
     *
     * @param teamId   团队 id
     * @param inviteId 邀请 id
     * @return 邀请
     */
    private TeamInvite getInvite(Long teamId, Long inviteId) {
        return inviteRepo.findByIdAndTeamId(inviteId, teamId)
                .orElseThrow(() -> new JsonException(400, "邀请不存在"));
    }

    /**
     * 判断邀请是否过期
     *
     * @param invite 邀请
     * @return 是否过期
     */
    private boolean isExpired(TeamInvite invite) {
        return invite.getExpireAt() != null && invite.getExpireAt().getTime() < System.currentTimeMillis();
    }

    /**
     * 校验角色不能为 OWNER
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
     * 字符串去空白后转 null
     *
     * @param s 原字符串
     * @return 处理结果
     */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 转换为邀请视图
     *
     * @param invite 邀请
     * @return 视图
     */
    private InviteVO toVO(TeamInvite invite) {
        return InviteVO.builder()
                .inviteId(invite.getId())
                .username(invite.getUsername())
                .email(invite.getEmail())
                .role(invite.getRole())
                .token(invite.getToken())
                .expireAt(invite.getExpireAt() == null ? null : invite.getExpireAt().getTime())
                .createAt(invite.getCreateAt() == null ? null : invite.getCreateAt().getTime())
                .expired(isExpired(invite))
                .status(invite.getStatus())
                .build();
    }

    /**
     * 写入审计日志
     *
     * @param teamId      团队 id
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
}
