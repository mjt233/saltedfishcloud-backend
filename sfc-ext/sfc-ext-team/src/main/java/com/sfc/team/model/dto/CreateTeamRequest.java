package com.sfc.team.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 创建团队请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTeamRequest {
    /**
     * 团队名称
     */
    @NotBlank(message = "团队名称不能为空")
    @Length(max = 64, message = "团队名称长度不能超过64")
    private String name;

    /**
     * 团队描述
     */
    @Length(max = 255, message = "团队描述长度不能超过255")
    private String description;
}
