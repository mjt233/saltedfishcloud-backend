package com.xiaotao.saltedfishcloud.service.file.store;

import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * 直接使用存储服务的原始文件路径（而不是网盘路径）进行资源操作的服务，提供目标文件存储系统最基础最原始的文件操作。<br>
 * 通常不直接使用，而是为其他涉及到文件存储操作的抽象类提供文件最基本的存储能力（如抽象文件存储服务，抽象用户配置数据服务）。
 */
public interface DirectRawStoreHandler {

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
     * @param path          包含文件名本身的保存路径
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
     * 将指定路径的文件复制为指定目标位置。
     * 不要求复制目录。
     * @param src   源路径
     * @param dest  目标路径
     * @param transferItem 当前正在复制的文件信息。如果复制过程中能够获取复制进度与文件大小，可修改该对象的loaded与total字段。
     */
    boolean copy(String src, String dest,@Nullable FileTransferItem transferItem) throws IOException;

    /**
     * 将指定路径的文件或目录移动为指定的路径。
     * @param src   源路径
     * @param dest  目标路径
     * @param transferItem 当前正在移动的文件信息。如果移动过程中能够获取复制进度与文件大小，可修改该对象的loaded与total字段。
     */
    boolean move(String src, String dest,@Nullable FileTransferItem transferItem) throws IOException;


    /**
     * 更新文件的时间信息。注意并非所有文件系统都支持此操作，调用可能不生效
     * @param path  文件所在的父目录
     * @param names 文件名列表
     * @param attribute 时间信息
     */
    void updateTime(String path, List<String> names, FileTimeAttribute attribute) throws IOException;


    /**
     * 判断路径是否为空目录
     * @param path 目录路径
     */
    boolean isEmptyDirectory(String path) throws IOException;

    /**
     * 获取指定路径的资源
     * @param path  路径
     * @return 当资源不存在或目标资源为目录时，应返回null
     */
    Resource getResource(String path) throws IOException;

    /**
     * 列出指定路径的文件列表
     * @param path 目录路径
     * @return 当指定路径不是有效目录时，应返回null。目录为空则返回空集合
     */
    List<FileInfo> listFiles(String path) throws IOException;


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
        try {
            return getFileInfo(path) != null;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
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

            if (parentPathInfo == null) {
                mkdirs(parentPath);
                return mkdir(path);
            }

            if (parentPathInfo.isDir()) {
                return mkdir(path);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
