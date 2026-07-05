package com.xiaotao.saltedfishcloud.model.param;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 评论列表查询参数，用于按话题ID或评论ID分页查询评论。
 */
@Data
@Accessors(chain = true)
public class CommentListParam {
    /**
     * 话题ID（listByTopicId 时使用）
     */
    private Long topicId;

    /**
     * 根评论ID（listByCommentId 时使用，用于查询该评论下的回复）
     */
    private Long commentId;

    /**
     * 分页请求参数
     */
    private PageableRequest pageableRequest;
}
