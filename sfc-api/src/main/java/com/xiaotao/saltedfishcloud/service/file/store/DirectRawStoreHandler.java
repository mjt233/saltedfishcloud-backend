package com.xiaotao.saltedfishcloud.service.file.store;

import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.StoreReader;
import com.xiaotao.saltedfishcloud.service.file.store.StoreWriter;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * 直接使用存储服务的原始文件路径（而不是网盘路径）进行资源操作的服务，提供目标文件存储系统最基础最原始的文件操作。
 * 通常不直接使用，而是为其他涉及到文件存储操作的抽象类提供文件最基本的存储能力（如抽象文件存储服务，抽象用户配置数据服务）。
 * @TODO 实现基于DirectRawStoreService提供存储能力的存储服务和用户配置数据服务抽象类）
 */
public interface DirectRawStoreHandler extends StoreReader, StoreWriter {


    /**
     * 同mkdir，但是是递归创建目录（即连同不存在的父级目录）
     * @param path 要创建的目录完整路径
     */
    @Override
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

//    /**
//     * 将指定路径的文件或目录复制到指定的路径。
//     * 若复制的为目录，且目标路径已存在，则合并内容。
//     * @param src   源路径
//     * @param dest  目标路径
//     */
//    @Override
//    default boolean copy(String src, String dest) throws IOException {
//        final FileInfo fileInfo = getFileInfo(src);
//        if (fileInfo != null && fileInfo.isDir()) {
//            throw new UnsupportedOperationException("未实现目录复制");
//        }
//        final Resource resource = getResource(src);
//        try(final InputStream is = resource.getInputStream()) {
//            return store(dest, is) == resource.contentLength();
//        }
//    }

//    /**
//     * 将指定路径的文件或目录移动为指定的路径。
//     * 若移动的为目录，且目标路径已存在，则合并内容。
//     * @param src   源路径
//     * @param dest  目标路径
//     */
//    @Override
//    default boolean move(String src, String dest) throws IOException {
//        final FileInfo fileInfo = getFileInfo(src);
//        if (fileInfo != null && fileInfo.isDir()) {
//            throw new UnsupportedOperationException("未实现目录移动");
//        }
//        final boolean copyRes = copy(src, dest);
//        final boolean deleteRes = delete(src);
//        return copyRes && deleteRes;
//    }
}
