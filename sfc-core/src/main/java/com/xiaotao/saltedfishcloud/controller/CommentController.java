package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
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

    @GetMapping("/listByTopicId")
    @AllowAnonymous
    public JsonResult<CommonPageInfo<CommentVo>> listByTopicId(Long topicId, PageableRequest pageableRequest) {
        return JsonResultImpl.getInstance(commentService.listByTopicId(topicId, pageableRequest));
    }

    /**
     * 发送/回复评论
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

    @PostMapping("sendPublicComment")
    @AllowAnonymous
    public JsonResult<Object> sendPublicComment(@RequestBody Comment comment, HttpServletRequest request) {
        comment.setIp(request.getRemoteAddr());
        comment.setTopicId(0L);
        commentService.sendComment(comment);
        return JsonResult.emptySuccess();
    }
}
