package com.sfc.archive.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;

import java.util.Date;

/**
 * 压缩任务资源，既可表示待压缩资源，也可表示压缩包内资源。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveResource {
    /**
     * 文件名（不含路径）。
     */
    private String name;

    /**
     * 文件大小，单位字节。
     */
    private Long size;

    /**
     * 资源在压缩包内的完整路径。
     */
    private String archivePath;

    /**
     * 是否为目录。
     */
    private Boolean isDirectory;

    /**
     * 最后修改时间。
     */
    private Date lastModified;

    /**
     * 创建时间。
     */
    private Date created;

    /**
     * 资源输入源；仅在写入压缩包时必填。
     */
    private Resource resource;
}

