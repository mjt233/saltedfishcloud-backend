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
     * 根据话题id获取评论
     * @param topicId   话题id
     */
    @Query("SELECT new com.xiaotao.saltedfishcloud.model.vo.CommentVo(" +
            "c.id, c.uid, c.createAt, c.updateAt, c.topicId, c.replyId, c.ip, c.content, c.isDelete, u.user) " +
            "FROM Comment c LEFT JOIN user u ON c.uid = u.id " +
            "WHERE c.topicId = :topicId " +
            "ORDER BY c.id DESC")
    Page<CommentVo> findByTopicId(@Param("topicId") Long topicId, Pageable pageable);
}
