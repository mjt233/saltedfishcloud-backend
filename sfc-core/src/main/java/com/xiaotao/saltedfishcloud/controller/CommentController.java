package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.service.comment.CommentService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
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
    public JsonResult listByTopicId(Long topicId, PageableRequest pageableRequest) {
        return JsonResultImpl.getInstance(
                commentService.listByTopicId(topicId, pageableRequest.getPage(), pageableRequest.getSize())
        );
    }

    @PostMapping("sendAnonymousComment")
    @AllowAnonymous
    public JsonResult sendComment(@RequestBody Comment comment, HttpServletRequest request) {
        comment.setIp(request.getRemoteAddr());
        commentService.sendComment(comment);
        return JsonResult.emptySuccess();
    }
}
