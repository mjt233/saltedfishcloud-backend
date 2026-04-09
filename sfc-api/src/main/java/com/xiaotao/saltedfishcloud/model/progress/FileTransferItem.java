package com.xiaotao.saltedfishcloud.model.progress;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 单个文件的传输/复制信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileTransferItem {
    /**
     * 对该文件的操作唯一标识
     */
    @Builder.Default
    private String actionId = UUID.randomUUID().toString();

    /**
     * 传输的文件信息
     */
    private FileInfo fileInfo;

    /**
     * 从哪里开始传输
     */
    private String from;

    /**
     * 传输到哪里
     */
    private String to;

    /**
     * 文件大小
     */
    private Long total;

    /**
     * 已传输大小
     */
    private Long loaded;

    /**
     * 是否跳过处理了
     */
    private Boolean isSkip;
}
