package com.xiaotao.saltedfishcloud.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class CollectionRecordDTO {
    private Long id;

    /**
     * 文件收集任务id
     */
    private Long cid;

    /**
     * 文件提交人用户id
     */
    private Long uid;

    /**
     * 提交的文件名
     */
    private String filename;

    /**
     * 文件大小
     */
    private Long size;

    /**
     * 提交的文件md5哈希值
     */
    private String md5;

    /**
     * 提交人ip地址
     */
    private String ip;

    /**
     * 提交人用户名
     */
    private String username;

    /**
     * 文件上传日期
     */
    private Date createdAt;

    public CollectionRecordDTO(Long id, Long cid, Long uid, String filename, Long size, String md5, String ip, String username, Date createdAt) {
        this.id = id;
        this.cid = cid;
        this.uid = uid;
        this.filename = filename;
        this.size = size;
        this.md5 = md5;
        this.ip = ip;
        this.username = username;
        this.createdAt = createdAt;
    }
}
