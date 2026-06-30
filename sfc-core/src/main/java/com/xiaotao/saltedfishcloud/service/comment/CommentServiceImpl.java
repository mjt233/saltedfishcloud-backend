package com.xiaotao.saltedfishcloud.service.comment;

import com.xiaotao.saltedfishcloud.dao.jpa.CommentRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.config.SysSafeConfig;
import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.model.vo.CommentVo;
import com.xiaotao.saltedfishcloud.service.BaseJpaService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import jakarta.validation.constraints.NotNull;
import java.util.List;

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
        comment.setTopicId(0L);
        comment.setUid(user == null ? 0L : user.getId());
        comment.setId(null);
        repo.save(comment);
    }

    @Override
    public CommonPageInfo<CommentVo> listByTopicId(@NotNull Long topicId, Integer page, Integer size) {
        CommonPageInfo<CommentVo> result = CommonPageInfo.of(repo.findByTopicId(topicId, PageRequest.of(
                page == null ? 1 : page,
                size == null ? 20 : size)));

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
