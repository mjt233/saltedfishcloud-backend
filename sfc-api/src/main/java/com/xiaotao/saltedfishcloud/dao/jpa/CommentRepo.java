package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.Comment;
import com.xiaotao.saltedfishcloud.model.vo.CommentVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface CommentRepo extends JpaRepository<Comment, Long> {
    /**
     * 根据话题id获取根评论（非回复），并统计每条根评论的回复数量。
     *
     * @param topicId  话题id
     * @param pageable 分页参数
     * @return 根评论分页列表，每条评论包含 replyCount
     */
    @Query("""
            SELECT new com.xiaotao.saltedfishcloud.model.vo.CommentVo(
            c.id, c.uid, c.createAt, c.updateAt, c.topicId, c.replyId, c.replyUid, c.atUid,
            c.ip, c.content, c.isDelete, u.user,
            (SELECT COUNT(r.id) FROM Comment r WHERE r.replyId = c.id))
            FROM Comment c LEFT JOIN user u ON c.uid = u.id
            WHERE c.topicId = :topicId AND c.replyId IS NULL
            ORDER BY c.id DESC""")
    Page<CommentVo> findRootByTopicId(@Param("topicId") Long topicId, Pageable pageable);

    /**
     * 根据根评论id分页查询该评论下的所有回复，并关联查询发送者用户名和被回复者用户名。
     * 使用子查询从 atUid 获取被回复者用户名。
     *
     * @param commentId 根评论id
     * @param pageable  分页参数
     * @return 回复分页列表，每条回复包含 replyUsername（被回复人用户名）
     */
    @Query("""
            SELECT new com.xiaotao.saltedfishcloud.model.vo.CommentVo(
            c.id, c.uid, c.createAt, c.updateAt, c.topicId, c.replyId, c.replyUid, c.atUid,
            c.ip, c.content, c.isDelete, u.user,
            (SELECT ru.user FROM user ru WHERE ru.id = c.atUid))
            FROM Comment c
            LEFT JOIN user u ON c.uid = u.id
            WHERE c.replyId = :commentId
            ORDER BY c.id ASC""")
    Page<CommentVo> findRepliesByCommentId(@Param("commentId") Long commentId, Pageable pageable);

    /**
     * 统计指定用户在指定话题下的根评论数量
     *
     * @param topicId 话题id
     * @param uid     用户id
     * @return 根评论数量
     */
    long countByTopicIdAndUidAndReplyIdIsNull(Long topicId, Long uid);

    /**
     * 统计指定IP在指定话题下的根评论数量（用于匿名用户场景）
     *
     * @param topicId 话题id
     * @param ip      IP地址
     * @return 根评论数量
     */
    long countByTopicIdAndIpAndReplyIdIsNull(Long topicId, String ip);

    /**
     * 查询指定用户在指定话题下最近发布的一条评论
     *
     * @param topicId 话题id
     * @param uid     用户id
     * @return 最近的一条评论
     */
    Optional<Comment> findTopByTopicIdAndUidOrderByIdDesc(Long topicId, Long uid);

    /**
     * 查询指定IP在指定话题下最近发布的一条评论（用于匿名用户场景）
     *
     * @param topicId 话题id
     * @param ip      IP地址
     * @return 最近的一条评论
     */
    Optional<Comment> findTopByTopicIdAndIpOrderByIdDesc(Long topicId, String ip);

    /**
     * 统计指定用户在指定根评论下的回复数量
     *
     * @param replyId 根评论id
     * @param uid     用户id
     * @return 回复数量
     */
    long countByReplyIdAndUid(Long replyId, Long uid);

    /**
     * 统计指定IP在指定根评论下的回复数量（用于匿名用户场景）
     *
     * @param replyId 根评论id
     * @param ip      IP地址
     * @return 回复数量
     */
    long countByReplyIdAndIp(Long replyId, String ip);
}
