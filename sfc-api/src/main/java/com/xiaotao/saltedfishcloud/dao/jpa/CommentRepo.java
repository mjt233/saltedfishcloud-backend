package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.model.vo.CommentVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface CommentRepo extends JpaRepository<Comment, Long> {
    /**
     * 根据话题id获取根评论（非回复），并统计每条根评论的回复数量。
     *
     * @param topicId  话题id
     * @param pageable 分页参数
     * @return 根评论分页列表，每条评论包含 replyCount
     */
    @Query("SELECT new com.xiaotao.saltedfishcloud.model.vo.CommentVo(" +
            "c.id, c.uid, c.createAt, c.updateAt, c.topicId, c.replyId, c.replyUid, " +
            "c.ip, c.content, c.isDelete, u.user, " +
            "(SELECT COUNT(r.id) FROM Comment r WHERE r.replyId = c.id)) " +
            "FROM Comment c LEFT JOIN user u ON c.uid = u.id " +
            "WHERE c.topicId = :topicId AND c.replyId IS NULL " +
            "ORDER BY c.id DESC")
    Page<CommentVo> findRootByTopicId(@Param("topicId") Long topicId, Pageable pageable);

    /**
     * 根据根评论id分页查询该评论下的所有回复，并关联查询发送者用户名和被回复者用户名。
     * 使用子查询获取被回复者用户名，避免对 user 实体进行双 JOIN 时实体名与字段名冲突。
     *
     * @param commentId 根评论id
     * @param pageable  分页参数
     * @return 回复分页列表，每条回复包含 replyUsername（被回复人用户名）
     */
    @Query("SELECT new com.xiaotao.saltedfishcloud.model.vo.CommentVo(" +
            "c.id, c.uid, c.createAt, c.updateAt, c.topicId, c.replyId, c.replyUid, " +
            "c.ip, c.content, c.isDelete, u.user, " +
            "(SELECT ru.user FROM user ru WHERE ru.id = c.replyUid)) " +
            "FROM Comment c " +
            "LEFT JOIN user u ON c.uid = u.id " +
            "WHERE c.replyId = :commentId " +
            "ORDER BY c.id ASC")
    Page<CommentVo> findRepliesByCommentId(@Param("commentId") Long commentId, Pageable pageable);
}
