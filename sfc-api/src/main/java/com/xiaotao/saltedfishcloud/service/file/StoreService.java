package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnableOverwriteException;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * 网盘文件存储服务，负责用户网盘文件物理数据的存取
 */
public interface StoreService {
    /**
     * 清空该存储系统所有的用户私人和公共网盘文件
     */
    void clear() throws IOException;

    /**
     * 是否为唯一存储服务（内容相同的文件只存一份）
     */
    boolean isUnique();

    /**
     * 获取一个在目标存储系统的临时目录上，用于以原始路径操作临时文件的文件操作器。
     * @return  临时存储服务
     */
    TempStoreService getTempFileHandler();

    /**
     * 获取原始存储服务，提供相同文件仅存一份的能力。<br>
     * 当当前存储服务为原始存储时，通过该方法获取到的实例应为自身。<br>
     * 当当前存储服务为唯一存储时，通过该方法获取到的实例的{@link StoreService#getUniqueStoreService()}方法应为当前存储服务实例对象，而不是新的。<br>
     * 即该getUniqueStoreService方法与{@link StoreService#getUniqueStoreService()}方法获取获取的实例对象应为相互引用的关系
     * @see StoreService#getUniqueStoreService()
     */
    StoreService getRawStoreService();

    /**
     * 获取唯一存储服务，提供文件存储路径与结构与用户网盘存储结构保持一致的能力。<br>
     * 当当前存储服务为唯一存储时，通过该方法获取到的实例应为自身。<br>
     * 当当前存储服务为原始存储时，通过该方法获取到的实例的{@link StoreService#getRawStoreService()}方法应为当前存储服务实例对象，而不是新的。<br>
     * 即该getUniqueStoreService方法与{@link StoreService#getRawStoreService()}方法获取获取的实例对象应为相互引用的关系
     * @see StoreService#getRawStoreService()
     */
    StoreService getUniqueStoreService();

    /**
     * 是否支持目录浏览，不支持则意味着lists方法永远返回空集合，同时也不支持记录同步机制
     * @TODO 同步机制使用canBrowse判断能否同步
     * @return 支持为true，否则为false
     */
    boolean canBrowse();


    /**
     * 获取指定目录的文件信息列表
     * @param uid   用户ID
     * @param path  请求目录路径
     * @return  文件信息列表，若目录路径不存在或路径为文件则返回null，空目录返回空列表
     */
    List<FileInfo> lists(int uid, String path) throws IOException;

    /**
     * 获取文件资源，若文件不存在或目标为文件夹则为null
     * @param uid   用户ID
     * @param path  文件所在路径
     * @param name  文件名
     * @return      文件资源
     */
    Resource getResource(int uid, String path, String name) throws IOException;

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
     * @TODO fileInfo改为filename
     * @param uid           用户ID
     * @param nativePath    本地文件路径
     * @param diskPath      网盘路径
     * @param fileInfo      文件信息
     */
    default void moveToSave(int uid, Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {
        throw new UnsupportedOperationException("不支持store操作");
    }

    /**
     * 在网盘中复制文件，若目录名相同则合并目录
     * @param uid     用户ID
     * @param source  所在网盘路径
     * @param targetId 目的地网盘的用户ID
     * @param target  目的地网盘路径
     * @param sourceName    源文件名
     * @param targetName    目标文件名
     * @param overwrite 是否覆盖，若非true，则跳过该文件
     */
    default void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, boolean overwrite) throws IOException {
        throw new UnsupportedOperationException("不支持copy操作");
    }

    /**
     * 向用户网盘目录中保存一个文件。
     * 若出现文件覆盖同名文件夹的情况，应该抛出异常
     * @param uid   用户ID 0表示公共
     * @param input 输入的文件
     * @param targetDir    保存到的目标网盘目录位置（注意：不是本地真是路径）
     * @param fileInfo 文件信息
     * @throws JsonException 存储文件出错
     * @throws DuplicateKeyException UNIQUE模式下两个不相同的文件发生MD5碰撞
     * @throws UnableOverwriteException 保存位置存在同名的目录
     */
    default void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws IOException {
        throw new UnsupportedOperationException("不支持store操作");
    }

    /**
     * 在网盘中移动文件，若目录名相同则合并目录
     * @param uid     用户ID
     * @param source  所在网盘路径
     * @param target  目的地网盘路径
     * @param name    文件名
     * @param overwrite 是否覆盖原文件
     */
    default void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        throw new UnsupportedOperationException("不支持move操作");
    }

    /**
     * 文件重命名
     * @param uid   用户ID 0表示公共
     * @param path  文件所在路径
     * @param oldName 旧文件名
     * @param newName 新文件名
     */
    default void rename(int uid, String path, String oldName, String newName) throws IOException {
        throw new UnsupportedOperationException("不支持rename操作");
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
        throw new UnsupportedOperationException("不支持mkdir操作");
    }

    /**
     * 删除一个唯一存储类型的文件
     * @param md5   文件MD5
     * @return      删除的文件和目录数
     */
    default int delete(String md5) throws IOException {
        throw new UnsupportedOperationException("不支持按md5进行delete操作");
    }

    /**
     * 删除本地文件（文件夹会连同所有子文件和目录）
     * @param uid 用户ID
     * @param path 文件所在网盘目录的路径
     * @param files 文件名
     * @return 删除的文件和文件夹总数
     */
    default long delete(int uid, String path, Collection<String> files) throws IOException {
        throw new UnsupportedOperationException("不支持delete操作");
    }
}
