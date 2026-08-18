package com.sfc.team.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 移除团队成员请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoveMemberRequest {
    /**
     * 团队 id
     */
    @NotNull(message = "团队id不能为空")
    private Long teamId;

    /**
     * 被移除成员的用户 id
     */
    @NotNull(message = "用户id不能为空")
    private Long uid;
}
