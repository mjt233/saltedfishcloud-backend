package com.sfc.team.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 修改团队信息请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTeamInfoRequest {
    /**
     * 团队 id（团队文件空间 uid）
     */
    @NotNull(message = "团队id不能为空")
    private Long teamId;

    /**
     * 团队名称（为空表示不修改）
     */
    @Length(max = 64, message = "团队名称长度不能超过64")
    private String name;

    /**
     * 团队描述（为空表示不修改）
     */
    @Length(max = 255, message = "团队描述长度不能超过255")
    private String description;
}
