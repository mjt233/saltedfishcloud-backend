package com.sfc.team.model.dto;

import com.sfc.team.constant.TeamRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 添加已注册用户为团队成员请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {
    /**
     * 团队 id
     */
    @NotNull(message = "团队id不能为空")
    private Long teamId;

    /**
     * 被添加的用户 id
     */
    @NotNull(message = "用户id不能为空")
    private Long uid;

    /**
     * 成员角色（READ/WRITE/MANAGE，不允许 OWNER）
     */
    @NotNull(message = "角色不能为空")
    private Integer role;
}
