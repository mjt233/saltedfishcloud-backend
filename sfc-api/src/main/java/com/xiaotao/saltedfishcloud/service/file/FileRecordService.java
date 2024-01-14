package com.xiaotao.saltedfishcloud.service.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.List;

/**
 * 为网盘系统提供不依赖存储服务的情况下提供文件系统文件信息的读取和搜索能力（仅文件信息，不包含文件内容）<br>
 * 一般情况下，文件记录服务的记录数据应与存储服务保持同步。在默认的文件系统接口{@link DiskFileSystem}实现中，所有文件信息读取功能均使用FileRecordService而不是{@link StoreService}<br>
 *
 */
public interface FileRecordService {

    /**
     * 判断给定的资源路径是否存在
     * @param uid   用户ID
     * @param path  资源所在目录
     * @param name  文件名
     * @return      存在true，不存在false
     */
    boolean exist(long uid, String path, String name);

    /**
     * 获取用户的文件信息
     * @param uid       用户ID
     * @param dirPath   文件所在的目录路径
     * @param name      文件名
     */
    FileInfo getFileInfo(long uid, String dirPath, String name);

    /**
     * 通过MD5获取文件
     * @param md5   文件MD5
     * @param limit 限制的长度
     * @return      文件信息列表
     */
    List<FileInfo> getFileInfoByMd5(String md5, int limit);


    /**
     * 操作数据库复制网盘文件或目录到指定目录下
     *
     * @param uid        用户ID
     * @param source     要复制的文件或目录所在目录
     * @param target     复制到的目标目录
     * @param targetId   复制到的目标目录所属用户ID
     * @param sourceName 要复制的文件或目录名
     * @param overwrite  是否覆盖已存在的文件
     */
    @Transactional(rollbackFor = Exception.class)
    void copy(long uid, String source, String target, long targetId, String sourceName, String targetName, boolean overwrite) throws NoSuchFileException;

    /**
     * 操作数据库移动网盘文件或目录到指定目录下
     *
     * @param uid       用户ID
     * @param source    网盘文件或目录所在目录
     * @param target    网盘目标目录
     * @param name      文件名
     * @param overwrite 是否覆盖原文件信息
     * @throws NoSuchFileException 当原目录或目标目录不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    void move(long uid, String source, String target, String name, boolean overwrite) throws NoSuchFileException;


    /**
     * 保存或更新文件信息记录
     * @param fileInfo  待保存数据，若存在id则直接按id保存
     * @param path      文件所在路径，若没有id 且 没有node 则按路径匹配数据
     * @return          保存后的文件信息，与参数是相同的对象引用
     */
    FileInfo saveRecord(FileInfo fileInfo, String path);

    FileInfo save(FileInfo fileInfo);

    /**
     * 按用户id和节点id列出文件列表
     * @param uid       用户id
     * @param nodeId    节点id
     * @return          文件列表
     */
    List<FileInfo> findByUidAndNodeId(Long uid, String nodeId);

    /**
     * 批量删除某个目录下的文件或文件夹，文件夹的所有子文件夹和文件也会被一同删除
     *
     * @param uid  用户ID 0表示公共
     * @param path 路径
     * @param name 文件名列表
     * @return 删除的文件列表
     */
    @Transactional(rollbackFor = Exception.class)
    List<FileInfo> deleteRecords(long uid, String path, Collection<String> name) throws NoSuchFileException;

    /**
     * 向数据库系统新建一个文件夹记录
     *
     * @param uid  用户ID
     * @param name 文件夹名称
     * @param path 所在路径
     * @throws DuplicateKeyException 当目标目录已存在时抛出
     * @throws NoSuchFileException   当父级目录不存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    String mkdir(long uid, String name, String path) throws NoSuchFileException;

    /**
     * 创建一个文件夹，若文件夹的祖先目录不存在，则一并创建
     *
     * @param uid  用户ID
     * @param path 要创建的文件夹完整网盘路径
     * @return 文件夹创建后的节点ID，若无文件夹成功创建则返回null
     */
    @Transactional(rollbackFor = Exception.class)
    String mkdirs(long uid, String path);

    /**
     * 对文件或文件夹进行重命名
     *
     * @param uid     用户ID
     * @param path    目标文件或文件夹所在路径
     * @param oldName 旧文件名
     * @param newName 新文件名
     */
    @Transactional(rollbackFor = Exception.class)
    void rename(long uid, String path, String oldName, String newName) throws NoSuchFileException;
}
