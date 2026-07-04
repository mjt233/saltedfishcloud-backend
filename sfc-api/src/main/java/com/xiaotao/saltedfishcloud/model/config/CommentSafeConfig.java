package com.xiaotao.saltedfishcloud.model.config;

import com.xiaotao.saltedfishcloud.annotations.*;
import lombok.Data;

/**
 * 评论内容安全过滤配置
 */
@Data
@ConfigPropertyEntity(
        groups = {
                @ConfigPropertiesGroup(id = "base", name = "基础配置"),
                @ConfigPropertiesGroup(id = "feature", name = "特性开关")
        },
        defaultKeyNameStrategy = ConfigKeyNameStrategy.CAMEL_CASE
)
public class CommentSafeConfig {

    /**
     * 是否开启内容安全过滤
     */
    @ConfigProperty(
            inputType = "switch",
            defaultValue = "false",
            describe = "开启内容安全过滤",
            isRow = true
    )
    private Boolean enabled;

    /**
     * 自定义敏感词列表，每行一个
     */
    @ConfigProperty(
            title = "自定义敏感词",
            describe = "每行一个自定义敏感词，配置后将在内置敏感词库的基础上增加这些词",
            inputType = "textarea",
            isRow = true
    )
    private String customSensitiveWords;

    /**
     * 敏感词匹配时是否忽略英文字母大小写
     */
    @ConfigProperty(
            title = "忽略大小写",
            inputType = "switch",
            defaultValue = "true",
            describe = "忽略大小写",
            group = "feature"
    )
    private Boolean ignoreCase;

    /**
     * 敏感词匹配时是否忽略全角半角差异
     */
    @ConfigProperty(
            title = "忽略全角半角",
            inputType = "switch",
            defaultValue = "true",
            describe = "忽略全角半角（如Ａ和A）",
            group = "feature"
    )
    private Boolean ignoreWidth;

    /**
     * 敏感词匹配时是否忽略重复字符
     */
    @ConfigProperty(
            title = "忽略重复字符",
            inputType = "switch",
            defaultValue = "false",
            describe = "忽略重复字符，将『好牛逼啊啊啊啊』中的重复字归约后再匹配（如『牛逼』）",
            group = "feature"
    )
    private Boolean ignoreRepeat;

}
