package com.xiaotao.saltedfishcloud.service.file.store;

import java.io.IOException;
import java.io.InputStream;

/**
 * 提供存储系统的资源创建与删除能力
 */
public interface StoreWriter {

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
     * 同mkdir，但是是递归创建目录（即连同不存在的父级目录）
     * @param path 要创建的目录完整路径
     */
    boolean mkdirs(String path) throws IOException;


    /**
     * 将指定路径的文件复制为指定目标位置。
     * 不要求复制目录。
     * @param src   源路径
     * @param dest  目标路径
     */
    boolean copy(String src, String dest) throws IOException;

    /**
     * 将指定路径的文件或目录移动为指定的路径。
     * @param src   源路径
     * @param dest  目标路径
     */
    boolean move(String src, String dest) throws IOException;
}
