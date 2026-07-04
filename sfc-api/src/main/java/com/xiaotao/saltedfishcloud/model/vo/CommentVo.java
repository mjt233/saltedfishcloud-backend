package com.xiaotao.saltedfishcloud.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
public class CommentVo implements Serializable {
    /**
     * 评论ID
     */
    private Long id;

    /**
     * 发送者用户ID
     */
    private Long uid;

    /**
     * 创建时间
     */
    private Date createAt;

    /**
     * 更新时间
     */
    private Date updateAt;

    /**
     * 话题ID
     */
    private Long topicId;

    /**
     * 根评论ID（为null则表示是根评论）
     */
    private Long replyId;

    /**
     * 被回复人的用户ID（仅回复有效）
     */
    private Long replyUid;

    /**
     * 被@的用户ID，由客户端传入，用于解析被回复人用户名
     */
    private Long atUid;

    /**
     * 发送者IP地址
     */
    private String ip;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 是否逻辑删除（0-未删除，1-已删除）
     */
    private Integer isDelete;

    /**
     * 发送者用户名
     */
    private String username;

    /**
     * 根评论的回复数量（仅 listByTopicId 查询根评论时有值）
     */
    private Long replyCount;

    /**
     * 被回复评论的发送者用户名（仅 listByCommentId 查询回复时有值，通过 replyUid JOIN user 解析）
     */
    private String replyUsername;

    /**
     * 用于JPQL查询根评论的构造方法（含 replyCount，供 listByTopicId 使用）
     *
     * @param id         评论ID
     * @param uid        发送者用户ID
     * @param createAt   创建时间
     * @param updateAt   更新时间
     * @param topicId    话题ID
     * @param replyId    根评论ID（根评论为null）
     * @param replyUid   被回复人用户ID（根评论为null）
     * @param atUid      被@的用户ID
     * @param ip         IP地址
     * @param content    评论内容
     * @param isDelete   是否删除
     * @param username   发送者用户名
     * @param replyCount 回复数量
     */
    public CommentVo(Long id, Long uid, Date createAt, Date updateAt, Long topicId,
                     Long replyId, Long replyUid, Long atUid, String ip, String content, Integer isDelete,
                     String username, Long replyCount) {
        this.id = id;
        this.uid = uid;
        this.createAt = createAt;
        this.updateAt = updateAt;
        this.topicId = topicId;
        this.replyId = replyId;
        this.replyUid = replyUid;
        this.atUid = atUid;
        this.ip = ip;
        this.content = content;
        this.isDelete = isDelete;
        this.username = username;
        this.replyCount = replyCount;
    }

    /**
     * 用于JPQL查询回复的构造方法（含 replyUsername，供 listByCommentId 使用）
     *
     * @param id            评论ID
     * @param uid           发送者用户ID
     * @param createAt      创建时间
     * @param updateAt      更新时间
     * @param topicId       话题ID
     * @param replyId       根评论ID
     * @param replyUid      被回复人用户ID
     * @param atUid         被@的用户ID
     * @param ip            IP地址
     * @param content       评论内容
     * @param isDelete      是否删除
     * @param username      发送者用户名
     * @param replyUsername 被回复人用户名（通过 atUid JOIN user 解析）
     */
    public CommentVo(Long id, Long uid, Date createAt, Date updateAt, Long topicId,
                     Long replyId, Long replyUid, Long atUid, String ip, String content, Integer isDelete,
                     String username, String replyUsername) {
        this.id = id;
        this.uid = uid;
        this.createAt = createAt;
        this.updateAt = updateAt;
        this.topicId = topicId;
        this.replyId = replyId;
        this.replyUid = replyUid;
        this.atUid = atUid;
        this.ip = ip;
        this.content = content;
        this.isDelete = isDelete;
        this.username = username;
        this.replyUsername = replyUsername;
    }
}
