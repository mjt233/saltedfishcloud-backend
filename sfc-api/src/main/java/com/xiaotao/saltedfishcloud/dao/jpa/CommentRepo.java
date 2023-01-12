package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface CommentRepo extends JpaRepository<Comment, Long> {
    /**
     * 根据话题id获取评论
     * @param topicId   话题id
     */
    @Query(value = "SELECT a.id, a.uid,a.topic_id,a.content,a.create_at,a.ip,a.is_delete,a.reply_id,a.update_at,u.user as username " +
            "FROM comment a " +
            "LEFT JOIN user u ON a.uid = u.id " +
            "WHERE a.topic_id = :topicId ORDER BY a.id DESC", nativeQuery = true)
    List<Comment> findByTopicId(@Param("topicId") Long topicId, Pageable pageable);
}
