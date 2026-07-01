package com.xiaotao.saltedfishcloud.service.comment;

import com.xiaotao.saltedfishcloud.dao.jpa.CommentRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.config.SysSafeConfig;
import com.xiaotao.saltedfishcloud.model.param.CommentListParam;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.model.vo.CommentVo;
import com.xiaotao.saltedfishcloud.service.BaseJpaService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class CommentServiceImpl extends BaseJpaService<CommentRepo> implements CommentService {
    @Autowired
    private SysSafeConfig sysSafeConfig;

    @Autowired
    private CommentSensitiveWordProvider sensitiveWordProvider;

    /**
     * 对IP地址进行脱敏处理
     *
     * @param ip 原始IP地址
     * @return 脱敏后的IP地址
     */
    private String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }
        // IPv4：保留前两段，其余用*遮掩，如 192.168.1.1 -> 192.168.*.*
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.", 3);
            return parts[0] + "." + parts[1] + ".*.*";
        }
        // IPv6：保留前两段，其余用*遮掩，如 2001:db8::1 -> 2001:db8:**
        if (ip.contains(":")) {
            String[] parts = ip.split(":", 3);
            return parts[0] + ":" + parts[1] + ":**";
        }
        return ip;
    }

    /**
     * 根据IP显示策略处理评论列表的IP地址
     *
     * @param records 评论列表
     */
    private void applyIpDisplayPolicy(List<CommentVo> records) {
        String ipDisplay = sysSafeConfig.getCommentIpDisplay();
        if (ipDisplay == null) {
            return;
        }
        for (CommentVo vo : records) {
            switch (ipDisplay) {
                case "partial":
                    vo.setIp(maskIp(vo.getIp()));
                    break;
                case "hide":
                    vo.setIp(null);
                    break;
                // case "full": 不做处理
            }
        }
    }

    @Override
    public void sendComment(Comment comment) {
        UserPrincipal user = SecureUtils.getSpringSecurityUser();
        if (!TypeUtils.toBoolean(sysSafeConfig.getAllowAnonymousComments())) {
            if (user == null || user.isPublicUser()) {
                throw new IllegalArgumentException("不允许匿名留言");
            }
        }
        if (comment.getReplyId() != null) {
            Comment target = repo.findById(comment.getReplyId())
                    .orElseThrow(() -> new IllegalArgumentException("回复的评论不存在"));
            if (!Objects.equals(target.getTopicId(), comment.getTopicId())) {
                throw new IllegalArgumentException("回复的评论不属于同一话题");
            }
            // 记录被回复的用户ID
            comment.setReplyUid(target.getUid());
            // 两层结构：如果目标也是回复，则解析到顶级父评论
            while (target.getReplyId() != null) {
                target = repo.findById(target.getReplyId())
                        .orElseThrow(() -> new IllegalArgumentException("回复的评论链异常"));
            }
            comment.setReplyId(target.getId());
        }
        // 内容安全过滤：检测敏感词（未开启过滤时 contains 返回 false）
        if (sensitiveWordProvider.contains(comment.getContent())) {
            throw new JsonException("评论内容包含敏感词，请修改后重新提交");
        }

        comment.setUid(user == null ? 0L : user.getId());
        comment.setId(null);
        repo.save(comment);
    }

    @Override
    public CommonPageInfo<CommentVo> listByTopicId(CommentListParam param) {
        PageableRequest pageableRequest = param.getPageableRequest();
        Page<CommentVo> rootPage = repo.findRootByTopicId(
                param.getTopicId(),
                PageRequest.of(pageableRequest.getPage(), pageableRequest.getSize())
        );
        CommonPageInfo<CommentVo> result = CommonPageInfo.of(rootPage);
        // 根据IP显示策略处理IP地址
        applyIpDisplayPolicy(result.getContent());
        return result;
    }

    @Override
    public CommonPageInfo<CommentVo> listByCommentId(CommentListParam param) {
        PageableRequest pageableRequest = param.getPageableRequest();
        Page<CommentVo> replyPage = repo.findRepliesByCommentId(
                param.getCommentId(),
                PageRequest.of(pageableRequest.getPage(), pageableRequest.getSize())
        );
        CommonPageInfo<CommentVo> result = CommonPageInfo.of(replyPage);
        // 根据IP显示策略处理IP地址
        applyIpDisplayPolicy(result.getContent());
        return result;
    }
}
