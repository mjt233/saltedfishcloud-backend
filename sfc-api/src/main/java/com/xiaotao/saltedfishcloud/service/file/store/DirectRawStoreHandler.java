package com.xiaotao.saltedfishcloud.service.file.store;

import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.StoreReader;
import com.xiaotao.saltedfishcloud.service.file.store.StoreWriter;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * 直接使用存储服务的原始文件路径（而不是网盘路径）进行资源操作的服务，提供目标文件存储系统最基础最原始的文件操作。<br>
 * 通常不直接使用，而是为其他涉及到文件存储操作的抽象类提供文件最基本的存储能力（如抽象文件存储服务，抽象用户配置数据服务）。
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
}
