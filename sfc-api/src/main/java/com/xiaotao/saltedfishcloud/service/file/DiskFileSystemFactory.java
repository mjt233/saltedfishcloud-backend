package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;

import java.util.Collection;
import java.util.Map;

public interface DiskFileSystemFactory {
    /**
     * 根据给定的参数获取对应的文件系统
     * @param params    参数map
     * @return  对应的文件系统
     * @throws FileSystemParameterException             参数错误导致的文件系统获取失败
     */
    DiskFileSystem getFileSystem(Map<String, Object> params) throws FileSystemParameterException;

    /**
     * 测试文件系统是否正常
     */
    void testFileSystem(DiskFileSystem fileSystem) throws FileSystemParameterException;

    /**
     * 清理无用文件系统缓存，会被定时任务定时调用，以达到通知清理缓存和释放不再使用的远程文件系统连接的目的。
     * @param params    可以保留的文件系统参数
     */
    default void clearCache(Collection<Map<String, Object>> params) {

    }

    /**
     * 获取该文件系统的描述信息，
     * @return  协议名称
     */
    DiskFileSystemDescribe getDescribe();
}
