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
                @ConfigPropertiesGroup(id = "comments", name = "留言与评论")
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

    /**
     * 评论内容安全过滤配置
     */
    @ConfigProperty(
            title = "评论内容安全过滤",
            defaultValue = "{}",
            describe = "对评论内容进行敏感词过滤和安全检查，开启后含敏感词的评论将被拦截",
            inputType = "form",
            group = "comments"
    )
    private CommentSafeConfig commentSafeConfig;

    /**
     * 评论速率限制配置
     */
    @ConfigProperty(
            title = "评论速率限制",
            defaultValue = "{}",
            describe = "限制用户的评论频率和数量",
            inputType = "form",
            group = "comments"
    )
    private CommentRateLimitConfig commentRateLimitConfig;
}
