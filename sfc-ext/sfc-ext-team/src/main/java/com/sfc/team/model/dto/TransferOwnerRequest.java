package com.sfc.team.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 转移团队所有权请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferOwnerRequest {
    /**
     * 团队 id
     */
    @NotNull(message = "团队id不能为空")
    private Long teamId;

    /**
     * 新所有者用户 id（须为当前正常成员）
     */
    @NotNull(message = "目标用户id不能为空")
    private Long targetUid;
}
