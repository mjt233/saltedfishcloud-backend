package com.xiaotao.saltedfishcloud.service.file;

import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * 用户自定义资源的存储服务。
 * 自定义资源包括头像，卡片背景，应用背景，评论图片等（目前只设计了头像）
 */
public interface CustomStoreService {

    /**
     * 获取一个文件的jpg缩略图，可以是图片、视频、文档等。
     * @param md5   文件的MD5
     * @return      图片缩略图的资源，若没有对应的缩略图则返回null
     */
    Resource getThumbnail(String md5) throws IOException;

    /**
     * 获取用户头像资源
     * @param uid   用户ID
     * @return      用户未设置头像时，为null
     */
    Resource getAvatar(int uid) throws IOException;

    /**
     * 获取默认头像资源
     */
    Resource getDefaultAvatar() throws IOException;

    /**
     * 保存用户头像
     * @param uid   用户ID
     * @param resource 头像资源
     */
    void saveAvatar(int uid, Resource resource) throws IOException;
}
