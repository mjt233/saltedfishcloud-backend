package com.xiaotao.saltedfishcloud.model.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 简单一些的文件传输参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimpleFileTransferParam {
    /**
     * 文件原位置用户id
     */
    private Long sourceUid;

    /**
     * 文件原所在的目录路径
     */
    private String sourcePath;

    /**
     * 待传输的文件名列表。当该参数为null则表示使用sourcePath下的所有文件。
     */
    private List<String> files;

    /**
     * 文件目标用户id
     */
    private Long targetUid;

    /**
     * 文件目标所在的目录路径
     */
    private String targetPath;

    /**
     * 是否覆盖同名文件
     */
    private Boolean isOverwrite;
}
