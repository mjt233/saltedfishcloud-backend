package com.sfc.team.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改成员角色请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberRoleRequest {
    /**
     * 团队 id
     */
    @NotNull(message = "团队id不能为空")
    private Long teamId;

    /**
     * 目标成员用户 id
     */
    @NotNull(message = "用户id不能为空")
    private Long uid;

    /**
     * 新角色（READ/WRITE/MANAGE，不允许 OWNER）
     */
    @NotNull(message = "角色不能为空")
    private Integer role;
}
