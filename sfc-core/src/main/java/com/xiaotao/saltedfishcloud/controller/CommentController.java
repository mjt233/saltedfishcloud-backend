package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.CommentListParam;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.model.vo.CommentVo;
import com.xiaotao.saltedfishcloud.service.comment.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/comment")
public class CommentController {
    @Autowired
    private CommentService commentService;

    /**
     * 按话题ID分页查询根评论（不包括回复），每条评论包含回复数量。
     *
     * @param topicId          话题ID
     * @param pageableRequest  分页参数
     * @return 根评论分页列表
     */
    @GetMapping("/listByTopicId")
    @AllowAnonymous
    public JsonResult<CommonPageInfo<CommentVo>> listByTopicId(Long topicId, PageableRequest pageableRequest) {
        CommentListParam param = new CommentListParam()
                .setTopicId(topicId)
                .setPageableRequest(pageableRequest);
        return JsonResultImpl.getInstance(commentService.listByTopicId(param));
    }

    /**
     * 按根评论ID分页查询该评论下的回复。
     *
     * @param commentId        根评论ID
     * @param pageableRequest  分页参数
     * @return 回复分页列表
     */
    @GetMapping("/listByCommentId")
    @AllowAnonymous
    public JsonResult<CommonPageInfo<CommentVo>> listByCommentId(Long commentId, PageableRequest pageableRequest) {
        CommentListParam param = new CommentListParam()
                .setCommentId(commentId)
                .setPageableRequest(pageableRequest);
        return JsonResultImpl.getInstance(commentService.listByCommentId(param));
    }

    /**
     * 发送/回复评论
     *
     * @param comment   评论对象
     */
    @PostMapping("sendComment")
    public JsonResult<Object> sendComment(@RequestBody Comment comment, HttpServletRequest request) {
        if (comment.getTopicId() == null || comment.getTopicId() == 0L) {
            throw new JsonException("未指定topicId 或 topicId不能为0");
        }
        comment.setIp(request.getRemoteAddr());
        commentService.sendComment(comment);
        return JsonResult.emptySuccess();
    }

    /**
     * 发送公共留言
     *
     * @param comment   评论对象
     */
    @PostMapping("sendPublicComment")
    @AllowAnonymous
    public JsonResult<Object> sendPublicComment(@RequestBody Comment comment, HttpServletRequest request) {
        comment.setIp(request.getRemoteAddr());
        comment.setTopicId(0L);
        commentService.sendComment(comment);
        return JsonResult.emptySuccess();
    }
}
