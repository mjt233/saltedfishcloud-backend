package com.sfc.cloud.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网盘文件信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SfcFileInfo {

    /** 目录类型 */
    public static final int TYPE_DIR = 1;

    /** 文件类型 */
    public static final int TYPE_FILE = 2;

    /** 文件记录 id */
    private Long id;

    /** 所属用户 id */
    private Long uid;

    /** 所在目录的节点 id */
    private String node;

    /** 文件名 */
    private String name;

    /** 文件 md5 */
    private String md5;

    /** 文件字节大小，-1 表示目录 */
    private Long size;

    /** 创建时间 */
    private Long ctime;

    /** 修改时间 */
    private Long mtime;

    /** 文件类型，1 目录、2 文件 */
    private Integer type;

    /** 挂载点 id */
    private Long mountId;

    /** 是否来自外部挂载的文件系统 */
    private Boolean isMount;

    /** 上级目录名称 */
    private String parent;

    /** 文件名后缀 */
    private String suffix;

    /** 是否为目录 */
    @JsonIgnore
    public boolean isDir() {
        if (size != null && size == -1L) {
            return true;
        }
        return type != null && type == TYPE_DIR;
    }

    /** 是否为文件 */
    @JsonIgnore
    public boolean isFile() {
        return !isDir();
    }
}
