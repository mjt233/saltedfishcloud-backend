package com.xiaotao.saltedfishcloud.service.file.thumbnail;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * 缩略图操作器，为生成缩略图提供能力支持
 */
public interface ThumbnailHandler {

    /**
     * 从流中读取数据数据生成缩略图
     * @param inputStream   原图输入流
     * @param type          文件类型
     * @param outputStream  缩略图输出流
     */
    void generate(InputStream inputStream, String type, OutputStream outputStream) throws IOException;

    /**
     * 获取支持的缩略图类型
     * @return  支持的缩略图类型
     */
    List<String> getSupportType();
}
