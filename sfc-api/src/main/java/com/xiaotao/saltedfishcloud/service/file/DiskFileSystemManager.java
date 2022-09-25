package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;

import java.util.Map;

/**
 * 网盘文件系统管理器，所有涉及到网盘文件的操作都应通过该接口获取{@link DiskFileSystem}来完成一系列操作。
 * 注意，不要缓存返回值用于下一次的一系列操作。因为可能存在多个文件系统实现，DiskFileSystemProvider能提供运行时文件系统的选择能力。
 */
public interface DiskFileSystemManager {
    DiskFileSystem getMainFileSystem();

    void registerFileSystem(DiskFileSystemFactory factory);

    void setMainFileSystem(DiskFileSystem fileSystem);

    DiskFileSystem getFileSystem(String protocol, Map<String, Object> params) throws FileSystemParameterException;

    boolean isSupportedProtocol(String protocol);
}
