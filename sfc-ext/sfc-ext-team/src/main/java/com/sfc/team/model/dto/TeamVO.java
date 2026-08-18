package com.sfc.team.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 团队列表/详情视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamVO {
    /**
     * 团队 id（即团队文件空间 uid）
     */
    private Long id;

    /**
     * 团队名称
     */
    private String name;

    /**
     * 团队描述
     */
    private String description;

    /**
     * 所有者用户 id
     */
    private Long ownerUid;

    /**
     * 当前用户在该团队的角色（1=READ 2=WRITE 3=MANAGE 4=OWNER）
     */
    private Integer myRole;

    /**
     * 正常成员数量
     */
    private Long memberCount;
}
