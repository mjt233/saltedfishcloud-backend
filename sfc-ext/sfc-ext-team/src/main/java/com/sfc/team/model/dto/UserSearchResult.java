package com.sfc.team.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户搜索结果（基础信息，用于添加成员联想）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchResult {
    /**
     * 用户 id
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
}
