package com.sfc.team.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 接受团队邀请请求（匿名公开接口）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcceptInviteRequest {
    /**
     * 邀请令牌
     */
    @NotBlank(message = "邀请令牌不能为空")
    private String token;

    /**
     * 用户名（邀请未预填用户名时必填）
     */
    @Length(max = 64, message = "用户名长度不能超过64")
    private String username;

    /**
     * 设置的密码
     */
    @NotBlank(message = "密码不能为空")
    @Length(min = 6, max = 64, message = "密码长度需在6-64之间")
    private String password;
}
