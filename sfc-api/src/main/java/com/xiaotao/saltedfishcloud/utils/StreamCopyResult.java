package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;

/**
 * 流复制结果汇总
 *
 * @param size 复制的字节数
 * @param md5  复制的数据的摘要
 */
public record StreamCopyResult(long size, String md5) {
    /**
     * 将md5和文件大小应用到文件信息对象中
     */
    public StreamCopyResult applyTo(FileInfo fileInfo) {
        fileInfo.setMd5(md5);
        fileInfo.setSize(size);
        return this;
    }

    public StreamCopyResult applyTo(ResourceRequest resourceRequest) {
        resourceRequest.setMd5(md5);
        resourceRequest.setSize(size);
        return this;
    }
}
