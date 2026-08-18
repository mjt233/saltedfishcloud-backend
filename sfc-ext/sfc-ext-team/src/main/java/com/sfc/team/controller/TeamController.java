package com.sfc.team.controller;

import com.sfc.team.model.dto.AcceptInviteRequest;
import com.sfc.team.model.dto.AddMemberRequest;
import com.sfc.team.model.dto.CreateInviteRequest;
import com.sfc.team.model.dto.CreateTeamRequest;
import com.sfc.team.model.dto.InviteIdRequest;
import com.sfc.team.model.dto.InviteInfoVO;
import com.sfc.team.model.dto.InviteVO;
import com.sfc.team.model.dto.RemoveMemberRequest;
import com.sfc.team.model.dto.TeamDetailVO;
import com.sfc.team.model.dto.TeamIdRequest;
import com.sfc.team.model.dto.TeamMemberListVO;
import com.sfc.team.model.dto.TeamVO;
import com.sfc.team.model.dto.TransferOwnerRequest;
import com.sfc.team.model.dto.UpdateMemberRoleRequest;
import com.sfc.team.model.dto.UpdateTeamInfoRequest;
import com.sfc.team.model.dto.UserSearchResult;
import com.sfc.team.service.TeamInviteService;
import com.sfc.team.service.TeamService;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 团队空间控制器。
 * <p>
 * 团队 id 即团队文件空间 uid，凡携带 teamId 的接口均以 {@link UID} 注解做成员级鉴权兜底，
 * 管理类操作再经服务层角色校验（MANAGE/OWNER）。
 */
@RestController
@RequestMapping("/api/team")
@Validated
@RequiredArgsConstructor
public class TeamController {

    /**
     * 团队服务
     */
    private final TeamService teamService;

    /**
     * 邀请服务
     */
    private final TeamInviteService inviteService;

    /**
     * 获取当前登录用户
     *
     * @return 当前用户
     */
    private UserPrincipal currentUser() {
        return SecureUtils.getSpringSecurityUser();
    }

    /**
     * 创建团队
     *
     * @param req 创建请求
     * @return 团队信息
     */
    @Operation(summary = "创建团队")
    @PostMapping("create")
    public JsonResult<TeamVO> create(@Valid @RequestBody CreateTeamRequest req) {
        return JsonResultImpl.getInstance(teamService.createTeam(currentUser(), req));
    }

    /**
     * 修改团队信息（MANAGE 及以上）
     *
     * @param req 修改请求
     * @return 修改后的团队信息
     */
    @Operation(summary = "修改团队信息")
    @PostMapping("updateInfo")
    public JsonResult<TeamVO> updateInfo(@Valid @RequestBody UpdateTeamInfoRequest req) {
        return JsonResultImpl.getInstance(teamService.updateInfo(currentUser(), req));
    }

    /**
     * 解散团队（仅 OWNER 或系统管理员）
     *
     * @param req 团队 id
     * @return 空结果
     */
    @Operation(summary = "解散团队")
    @PostMapping("delete")
    public JsonResult<Object> delete(@Valid @RequestBody TeamIdRequest req) {
        teamService.deleteTeam(currentUser(), req);
        return JsonResult.emptySuccess();
    }

    /**
     * 查询我的团队列表（管理员可查全部）
     *
     * @param all 是否查询全部团队
     * @return 团队列表
     */
    @Operation(summary = "查询我的团队列表")
    @GetMapping("list")
    public JsonResult<List<TeamVO>> list(@RequestParam(value = "all", required = false, defaultValue = "false") boolean all) {
        return JsonResultImpl.getInstance(teamService.listMyTeams(currentUser(), all));
    }

    /**
     * 查询团队详情
     *
     * @param teamId 团队 id
     * @param page   页码（0 起）
     * @param size   每页大小
     * @return 团队详情
     */
    @Operation(summary = "查询团队详情")
    @GetMapping("detail")
    public JsonResult<TeamDetailVO> detail(@RequestParam @UID Long teamId,
                                           @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                                           @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return JsonResultImpl.getInstance(teamService.getDetail(currentUser(), teamId, page, size));
    }

    /**
     * 查询团队成员列表（MANAGE 及以上）
     *
     * @param teamId 团队 id
     * @param page   页码（0 起）
     * @param size   每页大小
     * @return 成员列表（含待注册成员）
     */
    @Operation(summary = "查询团队成员列表")
    @GetMapping("memberList")
    public JsonResult<TeamMemberListVO> memberList(@RequestParam @UID Long teamId,
                                                   @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                                                   @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return JsonResultImpl.getInstance(teamService.memberList(currentUser(), teamId, page, size));
    }

    /**
     * 退出团队
     *
     * @param req 团队 id
     * @return 空结果
     */
    @Operation(summary = "退出团队")
    @PostMapping("quit")
    public JsonResult<Object> quit(@Valid @RequestBody TeamIdRequest req) {
        teamService.quit(currentUser(), req);
        return JsonResult.emptySuccess();
    }

    /**
     * 转移团队所有权（仅 OWNER）
     *
     * @param req 转移请求
     * @return 空结果
     */
    @Operation(summary = "转移团队所有权")
    @PostMapping("transferOwner")
    public JsonResult<Object> transferOwner(@Valid @RequestBody TransferOwnerRequest req) {
        teamService.transferOwner(currentUser(), req);
        return JsonResult.emptySuccess();
    }

    /**
     * 添加已注册用户为成员（MANAGE 及以上）
     *
     * @param req 添加请求
     * @return 空结果
     */
    @Operation(summary = "添加团队成员")
    @PostMapping("addMember")
    public JsonResult<Object> addMember(@Valid @RequestBody AddMemberRequest req) {
        teamService.addMember(currentUser(), req);
        return JsonResult.emptySuccess();
    }

    /**
     * 移除成员（MANAGE 及以上；不能移除 OWNER）
     *
     * @param req 移除请求
     * @return 空结果
     */
    @Operation(summary = "移除团队成员")
    @PostMapping("removeMember")
    public JsonResult<Object> removeMember(@Valid @RequestBody RemoveMemberRequest req) {
        teamService.removeMember(currentUser(), req);
        return JsonResult.emptySuccess();
    }

    /**
     * 修改成员角色（MANAGE 及以上；不能修改 OWNER）
     *
     * @param req 角色修改请求
     * @return 空结果
     */
    @Operation(summary = "修改成员角色")
    @PostMapping("updateMemberRole")
    public JsonResult<Object> updateMemberRole(@Valid @RequestBody UpdateMemberRoleRequest req) {
        teamService.updateMemberRole(currentUser(), req);
        return JsonResult.emptySuccess();
    }

    /**
     * 按用户名或邮箱搜索已注册用户（添加成员联想）
     *
     * @param keyword 关键词
     * @return 用户列表
     */
    @Operation(summary = "搜索用户")
    @GetMapping("searchUser")
    public JsonResult<List<UserSearchResult>> searchUser(@RequestParam String keyword) {
        return JsonResultImpl.getInstance(teamService.searchUsers(keyword));
    }

    /**
     * 创建邀请（MANAGE 及以上）
     *
     * @param req 创建请求
     * @return 邀请视图（含令牌）
     */
    @Operation(summary = "创建团队邀请")
    @PostMapping("createInvite")
    public JsonResult<InviteVO> createInvite(@Valid @RequestBody CreateInviteRequest req) {
        return JsonResultImpl.getInstance(inviteService.createInvite(currentUser(), req));
    }

    /**
     * 获取邀请链接（MANAGE 及以上，复制用）
     *
     * @param teamId   团队 id
     * @param inviteId 邀请 id
     * @return 邀请视图（含令牌）
     */
    @Operation(summary = "获取邀请链接")
    @GetMapping("getInviteLink")
    public JsonResult<InviteVO> getInviteLink(@RequestParam @UID Long teamId, @RequestParam @NotNull Long inviteId) {
        return JsonResultImpl.getInstance(inviteService.getInviteLink(currentUser(), teamId, inviteId));
    }

    /**
     * 撤销邀请（MANAGE 及以上）
     *
     * @param req 邀请 id
     * @return 空结果
     */
    @Operation(summary = "撤销邀请")
    @PostMapping("revokeInvite")
    public JsonResult<Object> revokeInvite(@Valid @RequestBody InviteIdRequest req) {
        inviteService.revokeInvite(currentUser(), req);
        return JsonResult.emptySuccess();
    }

    /**
     * 重新生成邀请链接（MANAGE 及以上）
     *
     * @param req 邀请 id
     * @return 新邀请视图
     */
    @Operation(summary = "重新生成邀请链接")
    @PostMapping("regenerateInvite")
    public JsonResult<InviteVO> regenerateInvite(@Valid @RequestBody InviteIdRequest req) {
        return JsonResultImpl.getInstance(inviteService.regenerateInvite(currentUser(), req));
    }

    /**
     * 查询邀请公开信息（受邀页展示，免登录）
     *
     * @param token 邀请令牌
     * @return 邀请公开信息
     */
    @Operation(summary = "查询邀请公开信息")
    @GetMapping("inviteInfo")
    @AllowAnonymous
    public JsonResult<InviteInfoVO> inviteInfo(@RequestParam String token) {
        return JsonResultImpl.getInstance(inviteService.inviteInfo(token));
    }

    /**
     * 接受邀请（公开）：注册账号并加入团队
     *
     * @param req 接受请求
     * @return 新用户 id
     */
    @Operation(summary = "接受团队邀请")
    @PostMapping("acceptInvite")
    @AllowAnonymous
    public JsonResult<Long> acceptInvite(@Valid @RequestBody AcceptInviteRequest req) {
        return JsonResultImpl.getInstance(inviteService.acceptInvite(req));
    }

    /**
     * 批量查询 uid 对应的团队名称（文件所有者展示映射）
     *
     * @param uids uid 列表
     * @return uid 到团队名称的映射
     */
    @Operation(summary = "批量查询团队名称")
    @PostMapping("fileOwnerInfo")
    public JsonResult<Map<Long, String>> fileOwnerInfo(@RequestBody List<Long> uids) {
        return JsonResultImpl.getInstance(teamService.fileOwnerInfo(uids));
    }
}
