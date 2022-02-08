package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 直接使用存储服务的原始文件路径（而不是网盘路径）进行资源操作的服务，提供目标文件存储系统最基础最原始的文件操作。
 * 通常不直接使用，而是为其他涉及到文件存储操作的抽象类提供文件最基本的存储能力（如抽象文件存储服务，抽象用户配置数据服务）。
 * @TODO 实现基于DirectRawStoreService提供存储能力的存储服务和用户配置数据服务抽象类）
 */
public interface DirectRawStoreService {
    /**
     * 获取指定路径的资源
     * @param path  路径
     * @return 当资源不存在时，应返回null
     */
    Resource getResource(String path) throws IOException;

    /**
     * 列出指定路径的文件列表
     * @param path 目录路径
     * @return 当指定路径不是有效目录时，应返回null。目录为空则返回空集合
     */
    List<FileInfo> listFiles(String path) throws IOException;

    /**
     * 删除指定路径下的文件，目录及其子目录的内容
     * @param path  要删除的路径
     */
    boolean delete(String path) throws IOException;

    /**
     * 创建文件夹
     * @param path 要创建的文件夹
     */
    boolean mkdir(String path) throws IOException;

    /**
     * 直接存储数据流为指定路径的文件
     * @param path          保存路径
     * @param inputStream   文件输入流
     * @return              保存的数据量大小（Byte）
     */
    long store(String path, InputStream inputStream) throws IOException;

    /**
     * 对文件资源进行重命名
     * @param path      文件完整路径
     * @param newName   新文件名
     */
    boolean rename(String path, String newName) throws IOException;

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
    default boolean exist(String path) throws IOException {
        return getFileInfo(path) != null;
    }

    /**
     * 同mkdir，但是是递归创建目录（即连同不存在的父级目录）
     * @param path 要创建的目录完整路径
     */
    default boolean mkdirs(String path) throws IOException {
        final FileInfo curPathInfo = getFileInfo(path);
        if (curPathInfo == null) {
            final String parentPath = PathUtils.getParentPath(path);
            final FileInfo parentPathInfo = getFileInfo(parentPath);
            if (parentPathInfo != null && parentPathInfo.isDir()) {
                return mkdir(parentPath);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * 将指定路径的文件或目录复制到指定的路径。
     * 若复制的为目录，且目标路径已存在，则合并内容。
     * @TODO 实现目录复制
     * @param src   源路径
     * @param dest  目标路径
     */
    default boolean copy(String src, String dest) throws IOException {
        final FileInfo fileInfo = getFileInfo(src);
        if (fileInfo != null && fileInfo.isDir()) {
            throw new UnsupportedOperationException("未实现目录复制");
        }
        final Resource resource = getResource(src);
        try(final InputStream is = resource.getInputStream()) {
            return store(dest, is) == resource.contentLength();
        }
    }

    /**
     * 将指定路径的文件或目录移动为指定的路径。
     * 若移动的为目录，且目标路径已存在，则合并内容。
     * @TODO 实现目录移动
     * @param src   源路径
     * @param dest  目标路径
     */
    default boolean move(String src, String dest) throws IOException {
        final FileInfo fileInfo = getFileInfo(src);
        if (fileInfo != null && fileInfo.isDir()) {
            throw new UnsupportedOperationException("未实现目录移动");
        }
        final boolean copyRes = copy(src, dest);
        final boolean deleteRes = delete(src);
        return copyRes && deleteRes;
    }
}
