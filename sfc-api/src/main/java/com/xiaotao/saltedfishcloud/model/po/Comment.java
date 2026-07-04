package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "comment",
        indexes = {
                @Index(name = "idx_topicId", columnList = "topicId"),
                @Index(name = "idx_replyId", columnList = "replyId")
        }
)
@SQLDelete(sql = "UPDATE Comment SET is_delete = 1 WHERE id = ?")
@SQLRestriction("is_delete = 0")
@Getter
@Setter
public class Comment extends AuditModel {
    /**
     * 评论主题id / 关联的业务主题id
     * 为null表示公共留言板
     */
    private Long topicId;

    /**
     * 表示该条消息所属的根评论ID。<br>
     * 回复层级最深为2层：话题(topicId) -> 话题下的评论 -> 对该评论的回复。<br>
     * 对于回复，replyId 始终指向话题下的根评论ID（而非直接被回复的那条），
     * 以便能按根评论ID一次查询出该评论下的所有回复。<br>
     * 对于话题下的评论（非回复），replyId 为 null。
     */
    private Long replyId;

    /**
     * 被回复人的用户ID。<br>
     * 仅对回复有效（replyId 不为 null 时），表示该条回复是回复哪个用户的。<br>
     * 对于话题下的根评论，replyUid 为 null。
     */
    private Long replyUid;

    /**
     * 被@的用户ID（对回复进行回复时，直接回复的目标用户）。
     * 由客户端传入，用于查询评论时解析被回复人用户名。
     */
    private Long atUid;

    /**
     * 评论发送人ip地址
     */
    private String ip;

    /**
     * 评论内容
     */
    @Lob
    private String content;

    private Integer isDelete = 0;
}