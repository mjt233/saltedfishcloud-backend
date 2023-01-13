package com.xiaotao.saltedfishcloud.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
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
}
