package com.xiaotao.saltedfishcloud.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
public class CommentVo implements Serializable {
    private Long id;
    private Long uid;
    private Date createAt;
    private Date updateAt;
    private Long topicId;
    private Long replyId;
    private String ip;
    private String content;
    private Integer isDelete;
    private String username;

    /**
     * 回复列表
     */
    private List<CommentVo> replies;

    /**
     * 被回复评论的发送者用户名
     */
    private String replyUsername;

    /**
     * 用于JPQL查询的构造方法
     */
    public CommentVo(Long id, Long uid, Date createAt, Date updateAt, Long topicId,
                     Long replyId, String ip, String content, Integer isDelete, String username) {
        this.id = id;
        this.uid = uid;
        this.createAt = createAt;
        this.updateAt = updateAt;
        this.topicId = topicId;
        this.replyId = replyId;
        this.ip = ip;
        this.content = content;
        this.isDelete = isDelete;
        this.username = username;
    }
}
