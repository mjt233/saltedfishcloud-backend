package com.xiaotao.saltedfishcloud.service.file.thumbnail;


import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * 缩略图服务，能为网盘系统中的文件生成缩略图资源。
 * 支持生成的文件则取决于注册的{@link ThumbnailHandler}
 * @see ThumbnailService#registerHandler(ThumbnailHandler)
 * @see ThumbnailHandler
 */
public interface ThumbnailService {
    /**
     * 获取缩略图资源
     * @param md5   文件的md5值
     * @param ext   文件拓展名（不带.）
     * @return      生成的缩略图资源，若为null则表示不支持或无法生成
     */
    Resource getThumbnail(String md5, String ext) throws IOException;

    /**
     * 向服务中新注册一个可用的缩略图操作器（提取器）
     * @param thumbnailHandler  缩略图操作器
     */
    void registerHandler(ThumbnailHandler thumbnailHandler);


    /**
     * 刷新注册
     */
    void refreshRegister();
}
