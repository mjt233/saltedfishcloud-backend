package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

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
    private Long topicId;

    private Long replyId;

    private String ip;

    private String content;

    private Integer isDelete = 0;
}