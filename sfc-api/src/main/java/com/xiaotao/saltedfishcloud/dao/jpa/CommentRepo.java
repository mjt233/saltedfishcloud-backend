package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.model.vo.CommentVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;


public interface CommentRepo extends JpaRepository<Comment, Long> {
    /**
     * 根据话题id获取根评论（非回复）
     * @param topicId   话题id
     */
    @Query("SELECT new com.xiaotao.saltedfishcloud.model.vo.CommentVo(" +
            "c.id, c.uid, c.createAt, c.updateAt, c.topicId, c.replyId, c.ip, c.content, c.isDelete, u.user) " +
            "FROM Comment c LEFT JOIN user u ON c.uid = u.id " +
            "WHERE c.topicId = :topicId AND c.replyId IS NULL " +
            "ORDER BY c.id DESC")
    Page<CommentVo> findRootByTopicId(@Param("topicId") Long topicId, Pageable pageable);

    /**
     * 根据话题id和回复目标id批量查询回复
     * @param topicId   话题id
     * @param replyIds  回复目标评论id集合
     */
    @Query("SELECT new com.xiaotao.saltedfishcloud.model.vo.CommentVo(" +
            "c.id, c.uid, c.createAt, c.updateAt, c.topicId, c.replyId, c.ip, c.content, c.isDelete, u.user) " +
            "FROM Comment c LEFT JOIN user u ON c.uid = u.id " +
            "WHERE c.topicId = :topicId AND c.replyId IN :replyIds " +
            "ORDER BY c.id ASC")
    List<CommentVo> findRepliesByTopicIdAndReplyIdIn(@Param("topicId") Long topicId, @Param("replyIds") Collection<Long> replyIds);
}
