package com.xiaotao.saltedfishcloud.model.config;

import com.xiaotao.saltedfishcloud.annotations.*;
import lombok.Data;

/**
 * 系统安全配置
 */
@Data
@ConfigPropertyEntity(
        prefix = "sys.safe",
        defaultKeyNameStrategy = ConfigKeyNameStrategy.UNDER_SCORE_CASE,
        groups = {
                @ConfigPropertiesGroup(id = "base", name = "常规"),
                @ConfigPropertiesGroup(id = "comments", name = "留言功能")
        }
)
public class SysSafeConfig {

    @ConfigProperty(
            title = "系统安全密钥",
            describe = "系统的安全密钥，修改后会导致所有用户登录失效、直链失效"
    )
    private String token;

    @ConfigProperty(
            title = "匿名留言",
            inputType = "switch",
            defaultValue = "false",
            describe = "允许匿名留言",
            group = "comments"
    )
    private Boolean allowAnonymousComments;

    @ConfigProperty(
            inputType = "select",
            title = "评论IP地址显示",
            defaultValue = "full",
            describe = "控制评论列表中IP地址的显示方式",
            group = "comments",
            options = {
                    @ConfigSelectOption(title = "完整显示", value = "full"),
                    @ConfigSelectOption(title = "部分显示", value = "partial"),
                    @ConfigSelectOption(title = "隐藏", value = "hide")
            }
    )
    private String commentIpDisplay;
}
