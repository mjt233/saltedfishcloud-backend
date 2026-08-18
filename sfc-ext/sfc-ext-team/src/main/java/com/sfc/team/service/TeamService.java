package com.sfc.team.service;

import com.sfc.team.constant.TeamRole;
import com.sfc.team.model.Team;
import com.sfc.team.model.TeamMember;
import com.sfc.team.model.dto.AddMemberRequest;
import com.sfc.team.model.dto.CreateTeamRequest;
import com.sfc.team.model.dto.RemoveMemberRequest;
import com.sfc.team.model.dto.TeamDetailVO;
import com.sfc.team.model.dto.TeamIdRequest;
import com.sfc.team.model.dto.TeamMemberListVO;
import com.sfc.team.model.dto.TeamVO;
import com.sfc.team.model.dto.TransferOwnerRequest;
import com.sfc.team.model.dto.UpdateMemberRoleRequest;
import com.sfc.team.model.dto.UpdateTeamInfoRequest;
import com.sfc.team.model.dto.UserSearchResult;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;

import java.util.List;
import java.util.Map;

/**
 * 团队管理服务：团队 CRUD、成员与角色管理、用户搜索与文件所有者映射。
 */
public interface TeamService {

    /**
     * 创建团队（创建者成为 OWNER）
     *
     * @param user 当前登录用户
     * @param req  创建请求
     * @return 团队信息
     */
    TeamVO createTeam(UserPrincipal user, CreateTeamRequest req);

    /**
     * 修改团队名称/描述（需 MANAGE 及以上）
     *
     * @param user 当前登录用户
     * @param req  修改请求
     * @return 修改后的团队信息
     */
    TeamVO updateInfo(UserPrincipal user, UpdateTeamInfoRequest req);

    /**
     * 解散团队（仅 OWNER 或系统管理员）：删除团队文件、置团队/成员/邀请状态并留存审计
     *
     * @param user 当前登录用户
     * @param req  团队 id
     */
    void deleteTeam(UserPrincipal user, TeamIdRequest req);

    /**
     * 查询当前用户加入的团队列表（管理员可查全部）
     *
     * @param user 当前登录用户
     * @param all  是否查询全部团队（仅系统管理员生效）
     * @return 团队列表（含我的角色与成员数）
     */
    List<TeamVO> listMyTeams(UserPrincipal user, boolean all);

    /**
     * 查询团队详情（团队成员）
     *
     * @param user    当前登录用户
     * @param teamId  团队 id
     * @param page    页码（0 起）
     * @param size    每页大小
     * @return 团队详情
     */
    TeamDetailVO getDetail(UserPrincipal user, Long teamId, int page, int size);

    /**
     * 查询团队成员列表（正常成员分页 + 待注册成员）
     *
     * @param user   当前登录用户
     * @param teamId 团队 id
     * @param page   页码（0 起）
     * @param size   每页大小
     * @return 成员列表视图
     */
    TeamMemberListVO memberList(UserPrincipal user, Long teamId, int page, int size);

    /**
     * 添加已注册用户为团队成员（需 MANAGE 及以上）
     *
     * @param user 操作者
     * @param req  添加请求
     */
    void addMember(UserPrincipal user, AddMemberRequest req);

    /**
     * 移除团队成员（需 MANAGE 及以上；不能移除 OWNER）
     *
     * @param user 操作者
     * @param req  移除请求
     */
    void removeMember(UserPrincipal user, RemoveMemberRequest req);

    /**
     * 修改成员角色（需 MANAGE 及以上；不能修改 OWNER 角色）
     *
     * @param user 操作者
     * @param req  角色修改请求
     */
    void updateMemberRole(UserPrincipal user, UpdateMemberRoleRequest req);

    /**
     * 退出团队（OWNER 需先转移所有权）
     *
     * @param user 当前登录用户
     * @param req  团队 id
     */
    void quit(UserPrincipal user, TeamIdRequest req);

    /**
     * 转移团队所有权（仅 OWNER；目标须为正常成员，原 OWNER 变为 MANAGE）
     *
     * @param user 操作者
     * @param req  转移请求
     */
    void transferOwner(UserPrincipal user, TransferOwnerRequest req);

    /**
     * 按用户名或邮箱搜索已注册用户（用于添加成员联想）
     *
     * @param keyword 搜索关键词
     * @return 用户基础信息列表
     */
    List<UserSearchResult> searchUsers(String keyword);

    /**
     * 批量查询 uid 对应的团队名称（用于团队空间文件所有者展示映射）
     *
     * @param uids uid 列表
     * @return uid 到团队名称的映射（非团队 uid 不包含在结果中）
     */
    Map<Long, String> fileOwnerInfo(List<Long> uids);

    /**
     * 校验用户在团队中的角色满足最低要求，不满足则抛出禁止异常
     *
     * @param user    当前登录用户
     * @param teamId  团队 id
     * @param minRole 要求的最低角色
     * @return 当前成员记录
     */
    TeamMember requireRole(UserPrincipal user, Long teamId, TeamRole minRole);

    /**
     * 查询用户在团队中的当前角色
     *
     * @param user   当前登录用户
     * @param teamId 团队 id
     * @return 角色；非成员返回 null
     */
    TeamRole getRole(UserPrincipal user, Long teamId);
}
