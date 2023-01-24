package com.xiaotao.saltedfishcloud.service.comment;

import com.xiaotao.saltedfishcloud.constant.FeatureName;
import com.xiaotao.saltedfishcloud.dao.jpa.CommentRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.vo.CommentVo;
import com.xiaotao.saltedfishcloud.service.BaseJpaService;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;

@Service
public class CommentServiceImpl extends BaseJpaService<CommentRepo> implements CommentService {
    @Autowired
    private HelloService helloService;

    private boolean isAllowAnonymousComment() {
        return TypeUtils.toBoolean(helloService.getDetail(FeatureName.ALLOW_ANONYMOUS_COMMENT));
    }

    @Override
    public void sendComment(Comment comment) {
        User user = SecureUtils.getSpringSecurityUser();
        if (!isAllowAnonymousComment() && (user == null || user.isPublicUser())) {
            throw new IllegalArgumentException("不允许匿名留言");
        }
        comment.setTopicId(0L);
        comment.setUid(user == null ? 0L : user.getId());
        comment.setId(null);
        repo.save(comment);
    }

    @Override
    public CommonPageInfo<CommentVo> listByTopicId(@NotNull Long topicId, Integer page, Integer size) {
        return CommonPageInfo.of(repo.findByTopicId(topicId, PageRequest.of(
                    page == null ? 1 : page,
                    size == null ? 20 : size)));
    }

}
