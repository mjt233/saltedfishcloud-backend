package com.sfc.team.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 为不存在的用户创建团队邀请请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInviteRequest {
    /**
     * 团队 id
     */
    @NotNull(message = "团队id不能为空")
    private Long teamId;

    /**
     * 预填用户名（可为空，接受时可自行填写）
     */
    @Length(max = 64, message = "用户名长度不能超过64")
    private String username;

    /**
     * 预填邮箱（可为空）
     */
    @Length(max = 128, message = "邮箱长度不能超过128")
    private String email;

    /**
     * 接受后生效的角色（READ/WRITE/MANAGE，不允许 OWNER）
     */
    @NotNull(message = "角色不能为空")
    private Integer role;
}
