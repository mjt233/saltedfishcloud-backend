package com.xiaotao.saltedfishcloud.compress.reader;


import java.io.InputStream;

/**
 * 压缩包文件访问器
 */
@FunctionalInterface
public interface ArchiveReaderVisitor {
    enum Result {
        CONTINUE, STOP, SKIP
    }

    /**
     * 访问一个压缩包内的文件
     * @param file      文件信息
     * @param stream    该文件的流
     * @return          当返回值为CONTINUE时，表示继续遍历，否则停止遍历
     */
    Result walk(CompressFile file, InputStream stream) throws Exception;
}
