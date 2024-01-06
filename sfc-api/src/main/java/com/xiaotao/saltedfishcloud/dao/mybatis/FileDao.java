package com.xiaotao.saltedfishcloud.dao.mybatis;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 文件信息dao
 * 后面会逐步迁移到使用jpa
 */
public interface FileDao {

    /**
     * 插入一条记录
     * @param fileInfo 文件信息
     * @return  受影响行数
     */
    int insert(@Param("file") FileInfo fileInfo);

    /**
     * 通过文件MD5获取文件信息
     * @param md5   文件MD5
     * @param limit 限制的文件个数
     * @return      文件信息列表
     */
    @Select("SELECT * FROM file_table WHERE md5 = #{md5} AND size != -1 LIMIT #{limit}")
    List<FileInfo> getFilesByMD5(@Param("md5") String md5, @Param("limit") int limit);

    /**
     * 取数据库中存在的有效的文件MD5
     * @param md5 文件MD5集合
     * @return 有效的MD5集合
     */
    List<String> getValidFileMD5s(@Param("md5s") Collection<String> md5);

    /**
     * 移动资源到指定目录下
     * @param uid   用户ID
     * @param nid   原文件资源所属节点ID
     * @param targetNodeId  目标节点ID
     * @param name  文件名
     * @return  受影响的行数
     */
    @Update("UPDATE file_table SET node=#{targetNodeId} WHERE node=#{nid} AND name=#{name} AND uid=#{uid}")
    int move(@Param("uid") Long uid,
                @Param("nid") String nid,
                @Param("targetNodeId") String targetNodeId,
                @Param("name") String name);

    /**
     * 获取用户某个节点下的所有文件
     * @param uid       用户ID
     * @param nodeId    节点ID
     * @return 文件信息列表
     */
    @Select("SELECT uid, name, node, size, md5, created_at, updated_at, mount_id FROM file_table WHERE node = #{nid} AND uid = #{uid}")
    List<FileInfo> getFileListByNodeId(@Param("uid") Long uid, @Param("nid") String nodeId);

    /**
     * 搜索某个用户的文件
     * @param uid       用户ID
     * @param key       文件名关键字
     * @return      文件信息列表
     */
    @Select("SELECT A.*, B.name AS parent FROM " +
            "file_table A LEFT JOIN node_list B ON " +
            " A.node = B.id " +
            "WHERE A.uid = #{uid} AND A.name like #{key} COLLATE utf8mb4_general_ci")
    List<FileInfo> search(@Param("uid") Long uid,
                                     @Param("key") String key);

    /**
     * 添加一条文件记录
     * @param uid 用户ID 0表示公共
     * @param fileName 文件名
     * @param size 文件大小
     * @param md5 文件md5
     * @param nodeId 文件所在路径（不包含文件名）的映射ID，路径ID需要用NodeDao或NodeService获取
     * @return 影响的行数
     */
    default int addRecord(Long uid, String fileName, Long size, String md5, String nodeId) {
        return addRecord(uid, fileName, size, md5, nodeId, new Date());
    }

    @Insert("INSERT IGNORE INTO file_table (uid, name, node, size, md5, created_at, updated_at, mount_id) VALUES (" +
            "#{fileInfo.uid}, #{fileInfo.name}, #{fileInfo.node}, #{fileInfo.size}, #{fileInfo.md5}, #{fileInfo.createdAt}, #{fileInfo.updatedAt}, #{fileInfo.mountId}" +
            ")")
    int addRecord(@Param("fileInfo") FileInfo fileInfo);

    /**
     * 添加一条文件记录
     * @param uid 用户ID 0表示公共
     * @param fileName 文件名
     * @param size 文件大小
     * @param md5 文件md5
     * @param nodeId 文件所在路径（不包含文件名）的映射ID，路径ID需要用NodeDao或NodeService获取
     * @param createAt 文件创建日期
     * @return 影响的行数
     */
    @Insert("INSERT IGNORE INTO file_table (uid,name,size,md5,node,created_at) VALUES (#{uid},#{name},#{size},#{md5},#{node},#{createAt})")
    int addRecord(@Param("uid") Long uid,
                  @Param("name") String fileName,
                  @Param("size") Long size,
                  @Param("md5") String md5,
                  @Param("node") String nodeId,
                 @Param("createAt") Date createAt);



    /**
     * 删除多条文件记录
     * @param uid 用户ID 0表示公共用户
     * @param node 文件路径ID
     * @param name 文件名
     * @return 受影响的行数
     */
    int deleteRecords(@Param("uid") Long uid,
                      @Param("node") String node,
                      @Param("name") List<String> name);

    /**
     * 删除1条文件记录
     * @param uid 用户ID 0表示公共用户
     * @param node 文件路径ID
     * @param name 文件名
     * @return 受影响的行数
     */
    @Delete("DELETE FROM file_table WHERE node = #{node} AND name = #{name} AND uid=#{uid}")
    int deleteRecord(@Param("uid") Long uid,
                     @Param("node") String node,
                     @Param("name") String name);


    /**
     * 批量删除某个文件节点下的文件夹记录
     * @param uid    用户ID 0表示公共
     * @param nodes  节点ID列表
     * @return 删除数
     */
    int deleteDirsRecord(@Param("uid") Long uid,
                         @Param("nodes") List<String> nodes
                      );

    /**
     * 更新文件记录
     * @param uid 用户ID 0表示公共用户
     * @param nodeId 原文件所在路径的ID
     * @param newSize 新文件大小
     * @param newMd5 新文件MD5
     * @return 受影响行数
     */
    @Update("UPDATE file_table SET md5=#{newMd5}, size=#{newSize}, updated_at=#{updateAt} WHERE node=#{node} AND uid=#{uid} AND name=#{name}")
    int updateRecord(@Param("uid") Long uid,
                     @Param("name") String name,
                     @Param("updateAt") Date updateAt,
                     @Param("node") String nodeId,
                     @Param("newSize") Long newSize,
                     @Param("newMd5") String newMd5);

    /**
     * 更新文件记录
     * @param uid 用户ID 0表示公共用户
     * @param nodeId 原文件所在路径的ID
     * @param newSize 新文件大小
     * @param newMd5 新文件MD5
     * @return 受影响行数
     */
    default int updateRecord(Long uid, String name, String nodeId, Long newSize, String newMd5) {
        return updateRecord(uid, name, new Date(), nodeId, newSize, newMd5);
    }

    /**
     * 获取文件信息
     * @param uid    用户ID 0表示公共
     * @param name   文件名
     * @param nodeId 文件所在节点ID
     * @return 文件信息
     */
    @Select("SELECT name, size, md5, node FROM file_table WHERE node=#{nodeId} AND name=#{name} AND uid=#{uid} ")
    FileInfo getFileInfo(@Param("uid") Long uid, @Param("name") String name, @Param("nodeId") String nodeId);

    /**
     * 批量获取某个目录下的文件信息
     * @param uid    用户ID 0表示公共
     * @param name   文件名列表
     * @param nodeId 路径ID
     * @return 删除数
     */
    List<FileInfo> getFilesInfo(@Param("uid") Long uid, @Param("names") Collection<String> name, @Param("nodeId") String nodeId);

    /**
     * 重命名文件或文件夹
     * @param uid   用户ID
     * @param nid   文件所在节点ID
     * @param oldName   旧文件名
     * @param newName   新文件名
     * @return  受影响的行数
     */
    @Update("UPDATE file_table SET name=#{newName} WHERE node=#{nid} AND name=#{oldName} AND uid=#{uid}")
    int rename(@Param("uid") Long uid,
               @Param("nid") String nid,
               @Param("oldName") String oldName,
               @Param("newName") String newName);
}
