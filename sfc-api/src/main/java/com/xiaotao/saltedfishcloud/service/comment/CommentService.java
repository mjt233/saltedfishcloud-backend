package com.xiaotao.saltedfishcloud.service.comment;

import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.CommentListParam;
import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.model.vo.CommentVo;

/**
 * 评论服务接口
 */
public interface CommentService {
    /**
     * 发送评论
     *
     * @param comment 评论对象
     */
    void sendComment(Comment comment);

    /**
     * 按话题获取根评论（不包括回复），每条评论包含回复数量。
     *
     * @param param 查询参数（topicId + 分页）
     * @return 根评论分页列表
     */
    CommonPageInfo<CommentVo> listByTopicId(CommentListParam param);

    /**
     * 按根评论ID分页查询该评论下的回复。
     *
     * @param param 查询参数（commentId + 分页）
     * @return 回复分页列表
     */
    CommonPageInfo<CommentVo> listByCommentId(CommentListParam param);
}
