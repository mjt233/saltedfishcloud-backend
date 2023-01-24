package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "comment")
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