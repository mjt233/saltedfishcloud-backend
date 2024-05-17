package com.xiaotao.saltedfishcloud.model.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileInfoSaveParam {
    /**
     * 用户id
     */
    private Long uid;

    /**
     * 文件md5
     */
    private String md5;

    /**
     * 文件名称
     */
    private String name;

    /**
     * 文件大小
     */
    private Long size;

    /**
     * 文件创建日期
     */
    private Long ctime;

    /**
     * 文件修改日期
     */
    private Long mtime;
}
