package com.sfc.team.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邀请操作请求（含团队 id 与邀请 id）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteIdRequest {
    /**
     * 团队 id
     */
    @NotNull(message = "团队id不能为空")
    private Long teamId;

    /**
     * 邀请 id
     */
    @NotNull(message = "邀请id不能为空")
    private Long inviteId;
}
