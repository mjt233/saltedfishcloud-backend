package com.xiaotao.saltedfishcloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件系统状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSystemStatus {
    /**
     * 公共网盘区域
     */
    public static final String AREA_PUBLIC = "public";

    /**
     * 私人网盘区域
     */
    public static final String AREA_PRIVATE = "private";

    /**
     * 属性所属区域
     */
    private String area;

    /**
     * 数据保存路径
     */
    private String path;

    /**
     * 剩余空间（字节）
     */
    private Long free;

    /**
     * 总空间（字节）
     */
    private Long total;

    /**
     * 已用空间（字节）
     */
    private Long used;

    /**
     * 文件数
     */
    private Long fileCount;

    /**
     * 目录数
     */
    private Long dirCount;

    /**
     * 咸鱼云文件系统使用的大小
     */
    private Long sysUsed;

    /**
     * 其他属性，深度为2
     */
    private List<ConfigNode> otherAttributes;
}
