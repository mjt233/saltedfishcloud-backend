package com.xiaotao.saltedfishcloud.service.file;

import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * 为存储服务提供唯一存储模式下的文件定位能力
 */
public interface FileResourceMd5Resolver {

    /**
     * 获取指定的用户文件路径的MD5值
     * @param uid   用户ID
     * @param path  网盘路径
     * @return      用户网盘路径对应的文件MD5值，若不存在则为null
     */
    String getResourceMd5(long uid, String path);

    /**
     * 判断某个文件是否仍在被用户引用
     * @param md5   待判断的文件MD5
     * @return      存在引用为true，否则为false
     */
    boolean hasRef(String md5);

    /**
     * 通过MD5获取文件资源
     * @param md5   文件MD5
     * @return      对应文件资源
     */
    Resource getResourceByMd5(String md5) throws IOException;
}
