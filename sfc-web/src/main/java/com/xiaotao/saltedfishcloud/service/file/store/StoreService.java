package com.xiaotao.saltedfishcloud.service.file.store;

import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnableOverwriteException;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public interface StoreService {
    /**
     * 获取指定目录的文件信息列表
     * @param uid   用户ID
     * @param path  请求目录路径
     * @return  文件信息列表，若目录路径不存在或路径为文件则返回null，空目录返回空列表
     */
    List<FileInfo> lists(int uid, String path) throws IOException;

    /**
     * 获取文件资源，若文件不存在则为null
     * @param uid   用户ID
     * @param path  文件所在路径
     * @param name  文件名
     * @return      文件资源
     */
    Resource getResource(int uid, String path, String name);

    /**
     * 判断给定的文件路径是否存在
     * @param uid   用户ID
     * @param path  文件或目录路径
     * @return  存在为true，否则为false
     */
    boolean exist(int uid, String path);

    /**
     * 通过文件移动的方式存储文件到网盘系统，相对于{@link #store}方法，避免了文件的重复写入操作。对本地文件操作后，原路径文件不再存在<br><br>
     * 如果是UNIQUE存储模式，则会先将文件移动到存储仓库（若仓库已存在文件则忽略该操作），随后再在目标网盘目录创建文件链接<br><br>
     * 如果是RAW存储模式，则会直接移动到目标位置。若本地文件路径与网盘路径对应的本地路径相同，操作将忽略。
     * @param uid           用户ID
     * @param nativePath    本地文件路径
     * @param diskPath      网盘路径
     * @param fileInfo      文件信息
     */
    default void moveToSave(int uid, Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * 在本地存储中复制用户网盘文件
     * @param uid     用户ID
     * @param source  所在网盘路径
     * @param target  目的地网盘路径
     * @param sourceName    文件名
     * @param overwrite 是否覆盖，若非true，则跳过该文件
     */
    default void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, Boolean overwrite) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * 向用户网盘目录中保存一个文件
     * @param uid   用户ID 0表示公共
     * @param input 输入的文件
     * @param targetDir    保存到的目标网盘目录位置（注意：不是本地真是路径）
     * @param fileInfo 文件信息
     * @throws JsonException 存储文件出错
     * @throws DuplicateKeyException UNIQUE模式下两个不相同的文件发生MD5碰撞
     * @throws UnableOverwriteException 保存位置存在同名的目录
     */
    default void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * 在本地存储中移动用户网盘文件
     * @param uid     用户ID
     * @param source  所在网盘路径
     * @param target  目的地网盘路径
     * @param name    文件名
     * @param overwrite 是否覆盖原文件
     */
    default void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * 文件重命名
     * @param uid   用户ID 0表示公共
     * @param path  文件所在路径
     * @param oldName 旧文件名
     * @param newName 新文件名
     */
    default void rename(int uid, String path, String oldName, String newName) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * 在本地文件系统中创建文件夹
     * @param uid   用户ID
     * @param path  所在路径
     * @param name  文件夹名
     * @throws FileAlreadyExistsException 目标已存在时抛出
     * @return 是否创建成功
     */
    default boolean mkdir(int uid, String path, String name) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * 删除一个唯一存储类型的文件
     * @param md5   文件MD5
     * @return      删除的文件和目录数
     */
    default int delete(String md5) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * 删除本地文件（文件夹会连同所有子文件和目录）
     * @param uid 用户ID
     * @param path 文件所在网盘目录的路径
     * @param files 文件名
     * @return 删除的文件和文件夹总数
     */
    default long delete(int uid, String path, Collection<String> files) throws IOException {
        throw new UnsupportedOperationException();
    }
}
