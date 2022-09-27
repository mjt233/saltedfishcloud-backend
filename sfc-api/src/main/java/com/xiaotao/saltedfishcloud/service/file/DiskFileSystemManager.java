package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;

import java.util.List;
import java.util.Map;

/**
 * 网盘文件系统管理器，所有涉及到网盘文件的操作都应通过该接口获取{@link DiskFileSystem}来完成一系列操作。
 * 注意，不要缓存返回值用于下一次的一系列操作。因为可能存在多个文件系统实现，DiskFileSystemProvider能提供运行时文件系统的选择能力。
 */
public interface DiskFileSystemManager {
    /**
     * 获取主文件系统
     */
    DiskFileSystem getMainFileSystem();

    /**
     * 注册一个新的文件系统
     */
    void registerFileSystem(DiskFileSystemFactory factory);

    /**
     * 获取所有公开可用的文件系统
     */
    List<DiskFileSystemFactory> listPublicFileSystem();

    /**
     * 获取所有的文件系统
     */
    List<DiskFileSystemFactory> listAllFileSystem();

    /**
     * 设置一个文件系统为主文件系统
     */
    void setMainFileSystem(DiskFileSystem fileSystem);

    /**
     * 根据协议和参数获取对应的文件系统实例
     * @param protocol  文件系统协议
     * @param params    参数
     * @return          文件系统实例
     * @throws FileSystemParameterException 参数错误导致的文件系统获取失败
     */
    DiskFileSystem getFileSystem(String protocol, Map<String, Object> params) throws FileSystemParameterException;

    /**
     * 判断所有注册的文件系统中是否含有支持指定协议的
     */
    boolean isSupportedProtocol(String protocol);
}
