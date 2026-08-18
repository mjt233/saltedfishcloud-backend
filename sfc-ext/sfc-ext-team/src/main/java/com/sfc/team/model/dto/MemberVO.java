package com.sfc.team.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 团队成员视图对象（已注册成员）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberVO {
    /**
     * 成员用户 id
     */
    private Long uid;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 成员角色（1=READ 2=WRITE 3=MANAGE 4=OWNER）
     */
    private Integer role;

    /**
     * 入团时间（时间戳）
     */
    private Long joinAt;
}
