package com.xiaotao.saltedfishcloud.model.param;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 评论发送参数，用于发送/回复评论
 */
@Data
@Accessors(chain = true)
public class CommentSendParam {
    /**
     * 评论内容
     */
    private String content;

    /**
     * 话题ID
     */
    private Long topicId;

    /**
     * 回复的根评论ID（为空则表示顶级评论）
     */
    private Long replyId;

    /**
     * 被@的用户ID，表示回复的目标用户。
     * 在对评论的回复进行回复时，用于标记被回复人。
     */
    private Long atUid;
}
