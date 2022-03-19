package com.xiaotao.saltedfishcloud.service.file.store;

import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

/**
 * 提供存储系统的资源读取能力
 */
public interface StoreReader {

    /**
     * 判断路径是否为空目录
     * @param path 目录路径
     */
    boolean isEmptyDirectory(String path) throws IOException;

    /**
     * 获取指定路径的资源
     * @param path  路径
     * @return 当资源不存在或目标资源为目录时，应返回null
     */
    Resource getResource(String path) throws IOException;

    /**
     * 列出指定路径的文件列表
     * @param path 目录路径
     * @return 当指定路径不是有效目录时，应返回null。目录为空则返回空集合
     */
    List<FileInfo> listFiles(String path) throws IOException;


    /**
     * 获取指定文件或目录的信息
     * @param path      需要获取信息的路径
     * @return 当路径不存在时应返回null
     */
    FileInfo getFileInfo(String path) throws IOException;

    /**
     * 检测指定路径是否为有效的文件或目录路径
     * @param path 待检测路径
     */
    default boolean exist(String path)  {
        try {
            return getFileInfo(path) != null;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
