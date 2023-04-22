package com.xiaotao.saltedfishcloud.service;

import com.sfc.enums.ArchiveType;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

/**
 * 网盘文件系统压缩与解压缩服务
 */
public interface DiskFileSystemArchiveService {
    /**
     * 创建压缩文件并直接输出到输出流中，可用于多文件打包下载
     * @param uid           用户ID
     * @param path          被打包压缩的文件所在路径
     * @param names         被压缩的文件名
     * @param type          压缩类型
     * @param outputStream  接收压缩数据的输出流
     * @return 任务id
     */
    void compressAndWriteOut(int uid, String path, Collection<String> names, ArchiveType type, OutputStream outputStream)
            throws IOException;

    /**
     * 创建一个压缩文件
     * @param uid   用户ID
     * @param path  输入的文件所在的网盘目录
     * @param names 要被压缩的文件名集合
     * @param dest  输出文件网盘路径
     * @param type  压缩类型
     * @return 任务id
     */
    void compress(int uid, String path, Collection<String> names, String dest, ArchiveType type) throws IOException;

    /**
     * 解压一个压缩包到指定目录下
     * @param uid   用户ID
     * @param path  压缩包所在路径
     * @param name  压缩包名称
     * @param dest  解压目的地
     * @return 任务id
     */
    void extractArchive(int uid, String path, String name, String dest) throws IOException;
}
