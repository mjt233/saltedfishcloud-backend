package com.xiaotao.saltedfishcloud.service.comment;

import com.xiaotao.saltedfishcloud.dao.jpa.CommentRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.config.SysSafeConfig;
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

import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl extends BaseJpaService<CommentRepo> implements CommentService {
    @Autowired
    private SysSafeConfig sysSafeConfig;

    /**
     * 对IP地址进行脱敏处理
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
            // 两层结构：如果目标也是回复，则解析到顶级父评论
            while (target.getReplyId() != null) {
                target = repo.findById(target.getReplyId())
                        .orElseThrow(() -> new IllegalArgumentException("回复的评论链异常"));
            }
            comment.setReplyId(target.getId());
        }
        comment.setUid(user == null ? 0L : user.getId());
        comment.setId(null);
        repo.save(comment);
    }

    @Override
    public CommonPageInfo<CommentVo> listByTopicId(@NotNull Long topicId, PageableRequest pageableRequest) {
        Page<CommentVo> rootPage = repo.findRootByTopicId(topicId, PageRequest.of(
                pageableRequest.getPage(),
                pageableRequest.getSize()
        ));
        CommonPageInfo<CommentVo> result = CommonPageInfo.of(rootPage);

        // 批量加载回复
        if (!rootPage.getContent().isEmpty()) {
            List<Long> rootIds = rootPage.getContent().stream()
                    .map(CommentVo::getId)
                    .collect(Collectors.toList());
            List<CommentVo> replies = repo.findRepliesByTopicIdAndReplyIdIn(topicId, rootIds);

            // 构建根评论ID -> 用户名映射（getUsername可能为null，避免Collectors.toMap抛出NPE）
            Map<Long, String> idToUsername = new HashMap<>();
            rootPage.getContent().forEach(vo -> idToUsername.put(vo.getId(), vo.getUsername()));

            // 分组回复并设置replyUsername
            Map<Long, List<CommentVo>> replyMap = new HashMap<>();
            for (CommentVo reply : replies) {
                replyMap.computeIfAbsent(reply.getReplyId(), k -> new ArrayList<>()).add(reply);
                reply.setReplyUsername(idToUsername.get(reply.getReplyId()));
            }

            // 关联回复到根评论
            for (CommentVo root : result.getContent()) {
                root.setReplies(replyMap.get(root.getId()));
            }
        }

        // 根据IP显示策略处理IP地址
        String ipDisplay = sysSafeConfig.getCommentIpDisplay();
        if (ipDisplay != null) {
            List<CommentVo> records = result.getContent();
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
        return result;
    }

}
