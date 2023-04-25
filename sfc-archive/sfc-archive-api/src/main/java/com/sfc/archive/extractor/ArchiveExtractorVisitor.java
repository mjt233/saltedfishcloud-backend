package com.sfc.archive.extractor;


import com.sfc.archive.model.ArchiveFile;

import java.io.InputStream;

/**
 * 压缩包文件访问器
 */
@FunctionalInterface
public interface ArchiveExtractorVisitor {
    enum Result {
        /**
         * 继续遍历
         */
        CONTINUE,
        /**
         * 终止遍历
         */
        STOP,
        /**
         * 跳过文件流
         */
        SKIP
    }

    /**
     * 访问一个压缩包内的文件
     * @param file      文件信息
     * @param stream    该文件的流（注意，若关闭了该流将导致访问器遍历终止，该流是对原始ArchiveInputStream的适配封装，限制只能读取当前遍历到的文件的流）
     * @return          当返回值为CONTINUE时，表示继续遍历，否则停止遍历
     */
    Result walk(ArchiveFile file, InputStream stream) throws Exception;
}
