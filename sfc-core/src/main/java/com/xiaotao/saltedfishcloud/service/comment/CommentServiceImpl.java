package com.xiaotao.saltedfishcloud.service.comment;

import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.dao.jpa.CommentRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.config.CommentRateLimitConfig;
import com.xiaotao.saltedfishcloud.model.config.SysSafeConfig;
import com.xiaotao.saltedfishcloud.model.param.CommentListParam;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.model.vo.CommentVo;
import com.xiaotao.saltedfishcloud.service.BaseJpaService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class CommentServiceImpl extends BaseJpaService<CommentRepo> implements CommentService {
    private final SysSafeConfig sysSafeConfig;
    private final CommentSensitiveWordProvider sensitiveWordProvider;
    private final CacheService cacheService;

    public CommentServiceImpl(SysSafeConfig sysSafeConfig,
                              CommentSensitiveWordProvider sensitiveWordProvider,
                              CacheService cacheService) {
        this.sysSafeConfig = sysSafeConfig;
        this.sensitiveWordProvider = sensitiveWordProvider;
        this.cacheService = cacheService;
    }

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
        boolean isAnonymous = user == null || user.isPublicUser();

        validateComment(comment, user, isAnonymous);

        comment.setUid(isAnonymous ? 0L : user.getId());
        comment.setId(null);
        repo.save(comment);
    }

    /**
     * 校验评论发送的各项限制规则
     *
     * @param comment     评论对象
     * @param user        当前用户
     * @param isAnonymous 是否匿名
     */
    private void validateComment(Comment comment, UserPrincipal user, boolean isAnonymous) {
        if (!TypeUtils.toBoolean(sysSafeConfig.getAllowAnonymousComments())) {
            if (isAnonymous) {
                throw new IllegalArgumentException("不允许匿名留言");
            }
        }

        CommentRateLimitConfig rateLimitConfig = sysSafeConfig.getCommentRateLimitConfig();

        // 最低留言间隔检查（对所有评论类型生效，使用缓存原子操作）
        if (rateLimitConfig != null) {
            Integer minInterval = rateLimitConfig.getMinCommentInterval();
            if (minInterval != null && minInterval != -1) {
                String identifier = isAnonymous ? comment.getIp() : String.valueOf(user.getId());
                String cacheKey = CacheKeyPrefixes.COMMENT_INTERVAL + comment.getTopicId() + ":" + identifier.hashCode();
                boolean allowed = cacheService.setIfAbsent(cacheKey, Boolean.TRUE, minInterval, TimeUnit.SECONDS);
                if (!allowed) {
                    throw new JsonException("留言过于频繁，请稍后再试");
                }
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

            // 最大回复数检查
            if (rateLimitConfig != null) {
                Integer maxReplies = rateLimitConfig.getMaxRepliesPerRootComment();
                if (maxReplies != null && maxReplies != -1) {
                    long replyCount;
                    if (isAnonymous) {
                        replyCount = repo.countByReplyIdAndIp(comment.getReplyId(), comment.getIp());
                    } else {
                        replyCount = repo.countByReplyIdAndUid(comment.getReplyId(), user.getId());
                    }
                    if (replyCount >= maxReplies) {
                        throw new JsonException("超过单用户最大回复数限制");
                    }
                }
            }
        } else {
            // 最大根评论数检查（仅根评论）
            if (rateLimitConfig != null) {
                Integer maxRoot = rateLimitConfig.getMaxRootCommentsPerUser();
                if (maxRoot != null && maxRoot != -1) {
                    long rootCount;
                    if (isAnonymous) {
                        rootCount = repo.countByTopicIdAndIpAndReplyIdIsNull(comment.getTopicId(), comment.getIp());
                    } else {
                        rootCount = repo.countByTopicIdAndUidAndReplyIdIsNull(comment.getTopicId(), user.getId());
                    }
                    if (rootCount >= maxRoot) {
                        throw new JsonException("超过单用户最大评论数限制");
                    }
                }
            }
        }

        // 内容安全过滤：检测敏感词（未开启过滤时 contains 返回 false）
        if (sensitiveWordProvider.contains(comment.getContent())) {
            throw new JsonException("评论内容包含敏感词，请修改后重新提交");
        }
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
