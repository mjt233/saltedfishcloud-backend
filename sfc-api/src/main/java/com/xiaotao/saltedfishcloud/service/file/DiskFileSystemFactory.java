package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;

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
     * 获取该文件系统的描述信息，
     * @return  协议名称
     */
    DiskFileSystemDescribe getDescribe();
}
