package com.xiaotao.saltedfishcloud.model.config;

import com.xiaotao.saltedfishcloud.annotations.*;
import lombok.Data;

/**
 * 评论速率限制配置
 */
@Data
@ConfigPropertyEntity(
        groups = {
                @ConfigPropertiesGroup(id = "rateLimit", name = "速率限制"),
                @ConfigPropertiesGroup(id = "quantityLimit", name = "数量限制")
        },
        defaultKeyNameStrategy = ConfigKeyNameStrategy.CAMEL_CASE
)
public class CommentRateLimitConfig {

    /**
     * 单用户在同一话题（topicId）下的最大根评论数量（replyId 为 null 的评论），
     * 超过此数量后继续发送根评论将抛出异常。设置为 -1 表示无限制。
     */
    @ConfigProperty(
            title = "单用户最大评论数",
            defaultValue = "-1",
            describe = "限制一个用户在同一话题下的最大根评论数量，-1表示无限制",
            group = "quantityLimit"
    )
    private Integer maxRootCommentsPerUser;

    /**
     * 单用户在同一话题下的最低留言频率间隔（单位：秒），
     * 用户在该时间间隔内只能发送一条评论（根评论或回复均可）。
     * 设置为 -1 表示无限制。
     */
    @ConfigProperty(
            title = "最低留言间隔",
            defaultValue = "5",
            describe = "限制一个用户在同一话题下发送评论的最小时间间隔（秒），-1表示无限制",
            group = "rateLimit"
    )
    private Integer minCommentInterval;

    /**
     * 单用户在一条根评论下的最大回复数量（replyId 不为 null 的评论），
     * 超过此数量后继续发送回复将抛出异常。设置为 -1 表示无限制。
     */
    @ConfigProperty(
            title = "单用户最大回复数",
            defaultValue = "-1",
            describe = "限制一个用户在一条根评论下的最大回复数量，-1表示无限制",
            group = "quantityLimit"
    )
    private Integer maxRepliesPerRootComment;
}
