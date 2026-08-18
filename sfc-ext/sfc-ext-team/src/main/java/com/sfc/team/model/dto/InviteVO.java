package com.sfc.team.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待注册成员（邀请）视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteVO {
    /**
     * 邀请 id
     */
    private Long inviteId;

    /**
     * 预填用户名（可为空）
     */
    private String username;

    /**
     * 预填邮箱（可为空）
     */
    private String email;

    /**
     * 接受后生效的角色（1=READ 2=WRITE 3=MANAGE）
     */
    private Integer role;

    /**
     * 邀请令牌（用于拼装复制链接）
     */
    private String token;

    /**
     * 过期时间（时间戳）
     */
    private Long expireAt;

    /**
     * 创建时间（时间戳）
     */
    private Long createAt;

    /**
     * 是否已过期
     */
    private Boolean expired;

    /**
     * 邀请状态（1=待接受 2=已接受 3=已撤销）
     */
    private Integer status;
}
