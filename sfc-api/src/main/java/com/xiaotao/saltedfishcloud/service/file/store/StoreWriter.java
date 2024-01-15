package com.xiaotao.saltedfishcloud.service.file.store;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
     * @param fileInfo      文件信息
     * @param path          保存路径
     * @param size          文件大小，-1为未知
     * @param inputStream   文件输入流，方法内不需要关闭该流，由外部调用方维护流的关闭操作。
     * @return              保存的数据量大小（Byte）
     */
    long store(FileInfo fileInfo, String path, long size, InputStream inputStream) throws IOException;

    /**
     * 获取目标路径文件资源的输出流
     * @param path          文件保存路径
     * @return              输出流，往流中写入的数据将写入存储系统
     * @throws IOException  任意IO错误
     */
    OutputStream newOutputStream(String path) throws IOException;

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
