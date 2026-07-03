package com.xiaotao.saltedfishcloud.service.comment;

import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.dao.jpa.CommentRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.config.CommentRateLimitConfig;
import com.xiaotao.saltedfishcloud.model.config.SysSafeConfig;
import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentRepo commentRepo;

    @Mock
    private SysSafeConfig sysSafeConfig;

    @Mock
    private CommentSensitiveWordProvider sensitiveWordProvider;

    @Mock
    private CacheService cacheService;

    private CommentServiceImpl commentService;

    private CommentRateLimitConfig rateLimitConfig;

    @BeforeEach
    void setUp() {
        commentService = new CommentServiceImpl(sysSafeConfig, sensitiveWordProvider, cacheService);
        ReflectionTestUtils.setField(commentService, "repo", commentRepo);

        rateLimitConfig = new CommentRateLimitConfig();
        rateLimitConfig.setMaxRootCommentsPerUser(-1);
        rateLimitConfig.setMinCommentInterval(-1);
        rateLimitConfig.setMaxRepliesPerRootComment(-1);
        lenient().when(sysSafeConfig.getCommentRateLimitConfig()).thenReturn(rateLimitConfig);
        lenient().when(sysSafeConfig.getAllowAnonymousComments()).thenReturn(true);
    }

    // ==================== maxRootCommentsPerUser ====================

    @Test
    void maxRootCommentsPerUser_negativeOne_shouldNotLimit() {
        UserPrincipal user = mockUser(1L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(user);
            when(sensitiveWordProvider.contains(any())).thenReturn(false);

            commentService.sendComment(createRootComment());

            verify(commentRepo).save(any());
        }
    }

    @Test
    void maxRootCommentsPerUser_exceeded_shouldThrow() {
        rateLimitConfig.setMaxRootCommentsPerUser(3);
        UserPrincipal user = mockUser(1L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(user);
            when(commentRepo.countByTopicIdAndUidAndReplyIdIsNull(anyLong(), anyLong())).thenReturn(3L);

            assertThrows(JsonException.class, () -> commentService.sendComment(createRootComment()));
            verify(commentRepo, never()).save(any());
        }
    }

    @Test
    void maxRootCommentsPerUser_notExceeded_shouldPass() {
        rateLimitConfig.setMaxRootCommentsPerUser(3);
        UserPrincipal user = mockUser(1L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(user);
            when(commentRepo.countByTopicIdAndUidAndReplyIdIsNull(anyLong(), anyLong())).thenReturn(2L);
            when(sensitiveWordProvider.contains(any())).thenReturn(false);

            commentService.sendComment(createRootComment());

            verify(commentRepo).save(any());
        }
    }

    @Test
    void maxRootCommentsPerUser_anonymous_shouldTrackByIp() {
        rateLimitConfig.setMaxRootCommentsPerUser(2);
        Comment comment = createRootComment();
        comment.setIp("192.168.1.1");
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(null);
            when(commentRepo.countByTopicIdAndIpAndReplyIdIsNull(anyLong(), anyString())).thenReturn(2L);

            assertThrows(JsonException.class, () -> commentService.sendComment(comment));
            verify(commentRepo).countByTopicIdAndIpAndReplyIdIsNull(comment.getTopicId(), comment.getIp());
        }
    }

    // ==================== minCommentInterval ====================

    @Test
    void minCommentInterval_negativeOne_shouldNotLimit() {
        UserPrincipal user = mockUser(1L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(user);
            when(sensitiveWordProvider.contains(any())).thenReturn(false);

            commentService.sendComment(createRootComment());

            verify(cacheService, never()).setIfAbsent(anyString(), any(), anyLong(), any());
            verify(commentRepo).save(any());
        }
    }

    @Test
    void minCommentInterval_allowed_shouldPass() {
        rateLimitConfig.setMinCommentInterval(5);
        UserPrincipal user = mockUser(1L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(user);
            when(cacheService.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(true);
            when(sensitiveWordProvider.contains(any())).thenReturn(false);

            commentService.sendComment(createRootComment());

            verify(commentRepo).save(any());
        }
    }

    @Test
    void minCommentInterval_denied_shouldThrow() {
        rateLimitConfig.setMinCommentInterval(5);
        UserPrincipal user = mockUser(1L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(user);
            when(cacheService.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(false);

            assertThrows(JsonException.class, () -> commentService.sendComment(createRootComment()));
            verify(commentRepo, never()).save(any());
        }
    }

    @Test
    void minCommentInterval_shouldApplyToReply() {
        rateLimitConfig.setMinCommentInterval(5);
        UserPrincipal user = mockUser(1L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(user);
            when(cacheService.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(false);

            assertThrows(JsonException.class, () -> commentService.sendComment(createReplyComment()));
            verify(commentRepo, never()).save(any());
        }
    }

    @Test
    void minCommentInterval_anonymous_shouldTrackByIp() {
        rateLimitConfig.setMinCommentInterval(5);
        Comment comment = createRootComment();
        comment.setIp("10.0.0.1");
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(null);
            when(cacheService.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(true);
            when(sensitiveWordProvider.contains(any())).thenReturn(false);

            commentService.sendComment(comment);

            verify(cacheService).setIfAbsent(contains(comment.getIp().hashCode() + ""), any(), eq(5L), any());
            verify(commentRepo).save(any());
        }
    }

    // ==================== maxRepliesPerRootComment ====================

    @Test
    void maxRepliesPerRootComment_negativeOne_shouldNotLimit() {
        UserPrincipal user = mockUser(1L);
        Comment target = createRootTarget(100L, 200L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(user);
            when(commentRepo.findById(anyLong())).thenReturn(Optional.of(target));
            when(sensitiveWordProvider.contains(any())).thenReturn(false);

            commentService.sendComment(createReplyComment());

            verify(commentRepo).save(any());
        }
    }

    @Test
    void maxRepliesPerRootComment_exceeded_shouldThrow() {
        rateLimitConfig.setMaxRepliesPerRootComment(3);
        UserPrincipal user = mockUser(1L);
        Comment target = createRootTarget(100L, 200L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(user);
            when(commentRepo.findById(anyLong())).thenReturn(Optional.of(target));
            when(commentRepo.countByReplyIdAndUid(anyLong(), anyLong())).thenReturn(3L);

            assertThrows(JsonException.class, () -> commentService.sendComment(createReplyComment()));
            verify(commentRepo, never()).save(any());
        }
    }

    @Test
    void maxRepliesPerRootComment_notExceeded_shouldPass() {
        rateLimitConfig.setMaxRepliesPerRootComment(3);
        UserPrincipal user = mockUser(1L);
        Comment target = createRootTarget(100L, 200L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(user);
            when(commentRepo.findById(anyLong())).thenReturn(Optional.of(target));
            when(commentRepo.countByReplyIdAndUid(anyLong(), anyLong())).thenReturn(2L);
            when(sensitiveWordProvider.contains(any())).thenReturn(false);

            commentService.sendComment(createReplyComment());

            verify(commentRepo).save(any());
        }
    }

    @Test
    void maxRepliesPerRootComment_anonymous_shouldTrackByIp() {
        rateLimitConfig.setMaxRepliesPerRootComment(2);
        Comment comment = createReplyComment();
        comment.setIp("192.168.1.100");
        Comment target = createRootTarget(100L, 200L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(null);
            when(commentRepo.findById(anyLong())).thenReturn(Optional.of(target));
            when(commentRepo.countByReplyIdAndIp(anyLong(), anyString())).thenReturn(2L);

            assertThrows(JsonException.class, () -> commentService.sendComment(comment));
            verify(commentRepo).countByReplyIdAndIp(target.getId(), comment.getIp());
        }
    }

    // ==================== Sensitive word still works ====================

    @Test
    void sensitiveWord_shouldStillBlock() {
        UserPrincipal user = mockUser(1L);
        try (MockedStatic<SecureUtils> ignored = mockStatic(SecureUtils.class)) {
            when(SecureUtils.getSpringSecurityUser()).thenReturn(user);
            when(sensitiveWordProvider.contains(any())).thenReturn(true);

            assertThrows(JsonException.class, () -> commentService.sendComment(createRootComment()));
            verify(commentRepo, never()).save(any());
        }
    }

    // ==================== Helpers ====================

    private static UserPrincipal mockUser(Long id) {
        UserPrincipal user = mock(UserPrincipal.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.isPublicUser()).thenReturn(false);
        return user;
    }

    private static Comment createRootComment() {
        Comment comment = new Comment();
        comment.setTopicId(100L);
        comment.setContent("test content");
        comment.setIp("127.0.0.1");
        return comment;
    }

    private static Comment createReplyComment() {
        Comment comment = new Comment();
        comment.setTopicId(100L);
        comment.setReplyId(1L);
        comment.setContent("test reply");
        comment.setIp("127.0.0.1");
        return comment;
    }

    private static Comment createRootTarget(Long topicId, Long commentId) {
        Comment target = new Comment();
        target.setId(commentId);
        target.setTopicId(topicId);
        target.setUid(2L);
        target.setReplyId(null);
        return target;
    }
}
