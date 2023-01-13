package com.xiaotao.saltedfishcloud.service.comment;

import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.model.vo.CommentVo;

/**
 * 评论服务接口
 */
public interface CommentService {
    /**
     * 发送评论
     */
    void sendComment(Comment comment);

    /**
     * 按话题获取评论
     */
    CommonPageInfo<CommentVo> listByTopicId(Long topicId, Integer page, Integer size);
}
