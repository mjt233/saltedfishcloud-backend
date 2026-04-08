package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressCallback;
import com.xiaotao.saltedfishcloud.service.node.FileTree;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * 为网盘系统提供不依赖存储服务的情况下提供文件系统文件信息的读取和搜索能力（仅文件信息，不包含文件内容）<br>
 * 一般情况下，文件记录服务的记录数据应与存储服务保持同步。在默认的文件系统接口{@link DiskFileSystem}实现中，所有文件信息读取功能均使用FileRecordService而不是{@link StoreService}<br>
 *
 */
public interface FileRecordService {

    /**
     * 获取用户文件树
     * @param uid   用户ID
     * @return      用户文件树
     */
    FileTree getFullTree(long uid);

    /**
     * 按文件路径获取文件信息
     * @param uid   用户ID
     * @param path  文件所在路径
     * @return      文件信息
     */
    Optional<FileInfo> getByPath(long uid, String path);


    /**
     * 使用广度优先遍历，列出指定目录下的所有类型为目录的文件信息
     * @param uid   用户id
     * @param node  要查询的目录所在节点
     * @param depth 遍历深度，0表示只获取一级目录，负数表示无限遍历
     * @return      不包括自己的指定遍历深度的所有子目录信息（不包括文件）
     */
    List<FileInfo> listChildDirs(long uid, String node, int depth);


    /**
     * 获取访问指定路径下途径的所有文件节点
     * @param uid   用户id
     * @param path  要查找的路径
     * @return      访问该路径时途径的所有 FileInfo 信息，首元素为起始节点，末元素为目标最终节点。对于 FileInfo#getNode() 为用户id的，表示该节点在根目录下。对于根目录，返回的队列长度为1。
     */
    Deque<FileInfo> getVisitPathInfo(long uid, String path);


    /**
     * 获取指定父节点下的指定名称的文件
     * @param uid       用户ID
     * @param parentId  父节点ID
     * @param name      文件名称
     * @return          文件信息，没有则返回null
     */
    FileInfo getByParentId(long uid, String parentId, String name);


    /**
     * 获取路径对应的节点<br>
     * @param uid   用户ID
     * @param path  请求的路径
     * @return  节点
     */
    Optional<String> getNodeIdByPath(long uid, String path);


    /**
     * 根据路径获取对应的节点对象
     * @param uid   用户id
     * @param path  路径
     */
    Optional<FileInfo> getNodeByPath(long uid, String path);


    /**
     * 通过目录节点ID 获取节点所在的完整路径位置。对于目录，FileInfo#getMd5即为目录节点id。
     * @param uid       用户ID
     * @param nodeId    目录节点ID(FileInfo的getMd5)
     * @return          完整路径
     */
    Optional<String> getPathByNodeId(long uid, String nodeId);


    /**
     * 根据节点id取出节点的所有父节点
     * @param uid       用户id
     * @param nodeId    查找的节点id
     * @return          所有父节点
     */
    Optional<Deque<FileInfo>> listAllParentByNodeId(long uid, String nodeId);


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
     * 按所属节点id查询文件信息
     * @param uid       用户id
     * @param nid       节点id
     * @param name      文件名
     */
    FileInfo getFileInfoByNode(long uid, String nid, String name);

    /**
     * 通过MD5获取文件
     * @param md5   文件MD5
     * @param limit 限制的长度
     * @return      文件信息列表
     */
    List<FileInfo> getFileInfoByMd5(String md5, int limit);

    /**
     * 根据目录节点id获取用户的目录信息
     * @param uid   用户id
     * @param md5   目录节点id（FileInfo#getMd5）
     */
    Optional<FileInfo> getDirByMd5(Long uid, String md5);

    /**
     * 操作数据库复制网盘文件或目录到指定目录下
     */
    void copy(SimpleFileTransferParam param,@Nullable CopyProgressCallback callback);

    /**
     * 在同一个目录中批量新增文件信息。如果path不存在会自动创建。如果已存在同名文件，会根据isOverwrite策略判断是否覆盖。
     * @param uid   文件所属的用户id
     * @param path  文件所在目录路径
     * @param isOverwrite 是否覆盖同名文件。当已存在的文件与源文件不是同为文件 或 不是同为文件夹时，会抛出异常。
     * @param fileInfos 要批量新增的文件
     */
    void batchSaveFileInSameDirectory(long uid, String path, boolean isOverwrite, List<FileInfo> fileInfos);

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
    void move(long uid, String source, String target, String name, boolean overwrite) throws NoSuchFileException;


    /**
     * 保存或更新文件信息记录
     * @param fileInfo  待保存数据，若存在id则直接按id保存
     * @param path      文件所在路径，若没有id 且 没有node 则按路径匹配数据
     * @return          保存后的文件信息，与参数是相同的对象引用
     */
    FileInfo saveRecord(FileInfo fileInfo, String path);

    /**
     * 直接保存文件信息，没有任何其他业务逻辑和校验
     */
    FileInfo save(FileInfo fileInfo);

    /**
     * 按用户id和节点id列出文件列表
     * @param uid       用户id
     * @param nodeId    节点id
     * @return          文件列表
     */
    List<FileInfo> findByUidAndNodeId(Long uid, String nodeId);

    /**
     * 按用户id、节点id和文件列表列出文件列表
     * @param uid       用户id
     * @param nodeId    节点id
     * @param nameList  限定的文件名列表，可为null表示不限制
     * @return          文件列表
     */
    List<FileInfo> findByUidAndNodeId(Long uid, String nodeId,@Nullable Collection<String> nameList);

    /**
     * 批量删除某个目录下的文件或文件夹，文件夹的所有子文件夹和文件也会被一同删除
     *
     * @param uid  用户ID 0表示公共
     * @param path 路径
     * @param name 文件名列表
     * @return 删除的文件列表
     */
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
    String mkdir(long uid, String name, String path) throws NoSuchFileException;


    /**
     * 创建一个文件夹，若文件夹的祖先目录不存在，则一并创建
     *
     * @param uid  用户ID
     * @param path 要创建的文件夹完整网盘路径
     * @param isMount 是否为挂载点目录
     * @return 文件夹创建后的节点ID，若无文件夹成功创建则返回null
     */
    String mkdirs(long uid, String path, boolean isMount);

    /**
     * 从数据库中获取指定文件夹节点id信息。如果该记录不存在则会创建一个。注意：如果存在数据库事务，该方法会在一个独立的新事务中执行。
     * @param uid   用户id
     * @param path  文件夹完整路径
     * @param isMount   需要创建记录时，是否标记为挂载目录
     * @return  指定路径的文件夹节点id(NodeInfo的id、FileInfo的md5）
     */
    String getAndMkdirs(long uid, String path, boolean isMount);

    /**
     * 删除文件记录。若文件是目录，则会连同本身、下级以及对应的NodeInfo与FileInfo记录一并删除。（注意：不检查权限）
     * @param id    文件记录FileInfo的id
     */
    void deleteFileInfo(long id);

    /**
     * 删除文件记录。若文件是目录，则会连同本身、下级以及对应的NodeInfo与FileInfo记录一并删除。（注意：不检查权限）
     * @param fileInfo    文件记录
     */
    void deleteFileInfo(FileInfo fileInfo);

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
