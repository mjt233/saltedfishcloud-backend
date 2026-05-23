package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferCallback;
import org.jetbrains.annotations.Nullable;

import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 文件系统元数据操作抽象，隔离默认实现对 FileRecordService 的直接依赖，便于后续按存储模式替换元数据策略。
 */
public interface FileSystemMetadataOperator {
    /**
     * 按完整路径获取文件记录。
     *
     * @param uid 用户ID
     * @param path 完整路径
     * @return 文件记录
     */
    Optional<FileInfo> getByPath(long uid, String path);

    /**
     * 根据文件MD5获取文件记录。
     *
     * @param md5 文件MD5
     * @param limit 返回数量上限
     * @return 文件记录列表
     */
    List<FileInfo> getFileInfoByMd5(String md5, int limit);

    /**
     * 根据节点ID获取路径。
     *
     * @param uid 用户ID
     * @param nodeId 节点ID
     * @return 对应路径
     */
    Optional<String> getPathByNodeId(long uid, String nodeId);

    /**
     * 保存文件记录。
     *
     * @param fileInfo 文件信息
     * @param path 所在目录
     */
    void saveRecord(FileInfo fileInfo, String path);

    /**
     * 直接保存文件记录实体。
     *
     * @param fileInfo 文件信息
     * @return 保存后的文件信息
     */
    FileInfo save(FileInfo fileInfo);

    /**
     * 判断指定路径是否存在。
     *
     * @param uid 用户ID
     * @param path 完整路径
     * @return 存在返回true，否则返回false
     */
    boolean exist(long uid, String path);

    /**
     * 获取指定目录下的文件记录。
     *
     * @param uid 用户ID
     * @param dirPath 目录路径
     * @param name 文件名
     * @return 文件记录
     */
    FileInfo getFileInfo(long uid, String dirPath, String name);

    /**
     * 按节点ID获取指定文件记录。
     *
     * @param uid 用户ID
     * @param nodeId 节点ID
     * @param name 文件名
     * @return 文件记录
     */
    FileInfo getFileInfoByNode(long uid, String nodeId, String name);

    /**
     * 根据路径获取节点ID。
     *
     * @param uid 用户ID
     * @param path 路径
     * @return 节点ID
     */
    Optional<String> getNodeIdByPath(long uid, String path);

    /**
     * 根据节点ID查询文件列表。
     *
     * @param uid 用户ID
     * @param nodeId 节点ID
     * @return 文件列表
     */
    List<FileInfo> findByUidAndNodeId(long uid, String nodeId);

    /**
     * 根据节点ID和文件名列表查询文件列表。
     *
     * @param uid 用户ID
     * @param nodeId 节点ID
     * @param nameList 文件名列表
     * @return 文件列表
     */
    List<FileInfo> findByUidAndNodeId(long uid, String nodeId, @Nullable Collection<String> nameList);

    /**
     * 复制文件记录。
     *
     * @param param 复制参数
     * @param callback 复制回调
     */
    void copy(SimpleFileTransferParam param, @Nullable FileTransferCallback callback);

    /**
     * 移动文件记录。
     *
     * @param uid 用户ID
     * @param source 源目录
     * @param target 目标目录
     * @param name 文件名
     * @param overwrite 是否覆盖
     * @throws NoSuchFileException 路径不存在时抛出
     */
    void move(long uid, String source, String target, String name, boolean overwrite) throws NoSuchFileException;

    /**
     * 在同目录下批量保存文件记录。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @param fileInfos 文件信息列表
     */
    void batchSaveFileInSameDirectory(long uid, String path, List<FileInfo> fileInfos);

    /**
     * 创建目录记录，若祖先目录不存在则一并创建。
     *
     * @param uid 用户ID
     * @param path 完整目录路径
     * @param isMount 是否为挂载目录
     * @return 创建后的节点ID
     */
    String mkdirs(long uid, String path, boolean isMount);

    /**
     * 获取目录节点ID，不存在时自动创建。
     *
     * @param uid 用户ID
     * @param path 完整目录路径
     * @param isMount 是否为挂载目录
     * @return 节点ID
     */
    String getAndMkdirs(long uid, String path, boolean isMount);

    /**
     * 删除文件记录。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @param names 文件名列表
     * @return 被删除的文件记录
     * @throws NoSuchFileException 路径不存在时抛出
     */
    List<FileInfo> deleteRecords(long uid, String path, Collection<String> names) throws NoSuchFileException;

    /**
     * 重命名文件记录。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @param name 原文件名
     * @param newName 新文件名
     * @throws NoSuchFileException 路径不存在时抛出
     */
    void rename(long uid, String path, String name, String newName) throws NoSuchFileException;

    /**
     * 更新文件记录中的时间属性。
     *
     * @param uid 用户ID
     * @param path 父目录路径
     * @param names 文件名列表
     * @param attribute 时间属性
     */
    void updateTime(long uid, String path, List<String> names, FileTimeAttribute attribute);

    /**
     * 按路径列出用户目录下的文件列表。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @return 文件列表数组
     */
    List<FileInfo>[] getUserFileList(long uid, String path);

    /**
     * 按路径和文件名列表列出文件。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @param nameList 文件名列表
     * @return 文件信息列表
     */
    List<FileInfo> getUserFileList(long uid, String path, @Nullable Collection<String> nameList);

    /**
     * 按节点ID列出文件列表。
     *
     * @param uid 用户ID
     * @param nodeId 节点ID
     * @return 文件列表数组
     */
    List<FileInfo>[] getUserFileListByNodeId(long uid, String nodeId);

    /**
     * 执行文件搜索。
     *
     * @param uid 用户ID
     * @param key 搜索关键字
     * @param page 页码
     * @param size 页大小
     * @return 搜索结果
     */
    CommonPageInfo<FileInfo> search(long uid, String key, Integer page, Integer size);

    /**
     * 合并主存储状态与文件记录统计状态。
     *
     * @param storeStatuses 主存储状态列表
     * @return 合并后的状态列表
     */
    List<FileSystemStatus> buildStatus(@Nullable List<FileSystemStatus> storeStatuses);
}
