package com.sfc.team.model;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import lombok.Data;

/**
 * 团队空间插件配置实体。
 */
@Data
@ConfigPropertyEntity(
        prefix = "team",
        groups = {
                @ConfigPropertiesGroup(id = "base", name = "基础配置", describe = "团队空间插件基础配置")
        }
)
public class TeamProperty {
    /**
     * 功能开关：是否启用团队空间
     */
    @ConfigProperty(describe = "开启团队空间功能", title = "功能开关", inputType = "switch", defaultValue = "true")
    private Boolean isEnabled = Boolean.TRUE;

    /**
     * 单个用户可创建的团队数量上限（null 表示不限）
     */
    @ConfigProperty(describe = "单个用户可创建的团队数量上限（留空表示不限）", title = "团队数量上限", inputType = "text", defaultValue = "")
    private Integer maxTeamsPerUser;

    /**
     * 邀请链接有效期（天），默认 7 天
     */
    @ConfigProperty(describe = "邀请链接有效期（天）", title = "邀请有效期", inputType = "text", defaultValue = "7")
    private Integer inviteExpireDays = 7;
}
