package com.sfc.archive.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

/**
 * 在网盘文件系统上操作压缩的参数
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class DiskFileSystemCompressParam {

    /**
     * 数据源用户id
     */
    private Long sourceUid;

    /**
     * 压缩的文件所在目录
     */
    private String sourcePath;

    /**
     * 压缩操作目录下的需要压缩的文件名（直接下级文件名）
     */
    private Collection<String> sourceNames;

    /**
     * 压缩完成，需要保存压缩结果的目标用户id
     */
    private Long targetUid;

    /**
     * 目标用户网盘下的压缩文件完整路径
     */
    private String targetFilePath;

    /**
     * 压缩参数
     */
    private ArchiveParam archiveParam;

}
