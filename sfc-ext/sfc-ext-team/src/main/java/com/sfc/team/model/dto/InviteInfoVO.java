package com.sfc.team.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公开受邀页展示的邀请信息（匿名可读）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteInfoVO {
    /**
     * 团队名称
     */
    private String teamName;

    /**
     * 邀请人用户名
     */
    private String inviterName;

    /**
     * 预填用户名（可为空）
     */
    private String username;

    /**
     * 预填邮箱（可为空）
     */
    private String email;

    /**
     * 邀请是否已过期
     */
    private Boolean expired;

    /**
     * 邀请是否仍有效（待接受且未过期）
     */
    private Boolean valid;
}
