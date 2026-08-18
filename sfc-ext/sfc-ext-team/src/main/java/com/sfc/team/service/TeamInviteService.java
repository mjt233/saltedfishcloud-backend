package com.sfc.team.service;

import com.sfc.team.model.TeamInvite;
import com.sfc.team.model.dto.AcceptInviteRequest;
import com.sfc.team.model.dto.CreateInviteRequest;
import com.sfc.team.model.dto.InviteIdRequest;
import com.sfc.team.model.dto.InviteInfoVO;
import com.sfc.team.model.dto.InviteVO;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;

/**
 * 团队邀请服务：为不存在的用户创建待注册成员，管理邀请链接全生命周期。
 */
public interface TeamInviteService {

    /**
     * 创建邀请（需 MANAGE 及以上），生成单次使用、默认 7 天有效的令牌
     *
     * @param user 操作者
     * @param req  创建请求
     * @return 邀请视图（含令牌）
     */
    InviteVO createInvite(UserPrincipal user, CreateInviteRequest req);

    /**
     * 获取当前邀请链接信息（需 MANAGE 及以上，不改变邀请状态）
     *
     * @param user     操作者
     * @param teamId   团队 id
     * @param inviteId 邀请 id
     * @return 邀请视图（含令牌）
     */
    InviteVO getInviteLink(UserPrincipal user, Long teamId, Long inviteId);

    /**
     * 撤销邀请（需 MANAGE 及以上），令牌即时作废
     *
     * @param user 操作者
     * @param req  邀请 id
     */
    void revokeInvite(UserPrincipal user, InviteIdRequest req);

    /**
     * 重新生成邀请链接（需 MANAGE 及以上）：旧令牌作废、重新计时
     *
     * @param user 操作者
     * @param req  邀请 id
     * @return 新邀请视图（含新令牌）
     */
    InviteVO regenerateInvite(UserPrincipal user, InviteIdRequest req);

    /**
     * 按令牌查询邀请公开信息（受邀页展示）
     *
     * @param token 邀请令牌
     * @return 邀请公开信息
     */
    InviteInfoVO inviteInfo(String token);

    /**
     * 接受邀请（公开）：校验令牌后创建咸鱼云账号并加入团队
     *
     * @param req 接受请求
     * @return 新创建的用户 id
     */
    Long acceptInvite(AcceptInviteRequest req);
}
