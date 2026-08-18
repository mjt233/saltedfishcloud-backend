package com.sfc.team.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仅含团队 id 的通用请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamIdRequest {
    /**
     * 团队 id（团队文件空间 uid）
     */
    @NotNull(message = "团队id不能为空")
    private Long teamId;
}
