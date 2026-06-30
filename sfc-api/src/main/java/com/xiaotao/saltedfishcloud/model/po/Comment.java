package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "comment",
        indexes = {
                @Index(name = "idx_topicId", columnList = "topicId")
        }
)
@SQLDelete(sql = "UPDATE Comment SET is_delete = 1 WHERE id = ?")
@Getter
@Setter
public class Comment extends AuditModel {
    /**
     * 评论主题id / 关联的业务主题id
     * 为null表示公共留言板
     */
    private Long topicId;

    /**
     * 表示该条消息是回复哪条评论下的
     */
    private Long replyId;

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