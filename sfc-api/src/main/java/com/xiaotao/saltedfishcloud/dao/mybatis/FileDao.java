package com.xiaotao.saltedfishcloud.dao.mybatis;

import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface FileDao {

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
    @Select({
            "<script>",
                "SELECT md5 FROM file_table WHERE md5 in ",
                "<foreach collection='md5' item='md5' open='(' separator=',' close=')'>",
                "#{md5}",
                "</foreach>" +
                " GROUP BY md5",
            "</script>"
    })
    List<String> getValidFileMD5s(Collection<String> md5);

    /**
     * 移动资源到指定目录下
     * @param uid   用户ID
     * @param nid   原文件资源所属节点ID
     * @param targetNodeId  目标节点ID
     * @param name  文件名
     * @return  受影响的行数
     */
    @Update("UPDATE file_table SET node=#{targetNodeId} WHERE node=#{nid} AND name=#{name} AND uid=#{uid}")
    int move(@Param("uid") Integer uid,
                @Param("nid") String nid,
                @Param("targetNodeId") String targetNodeId,
                @Param("name") String name);

    /**
     * 获取用户某个节点下的所有文件
     * @param uid       用户ID
     * @param nodeId    节点ID
     * @return 文件信息列表
     */
    @Select("SELECT uid, name, node, size, md5, created_at, updated_at FROM file_table WHERE node = #{nid} AND uid = #{uid}")
    List<FileInfo> getFileListByNodeId(@Param("uid") Integer uid, @Param("nid") String nodeId);

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
    List<FileInfo> search(@Param("uid") Integer uid,
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
    @Insert("INSERT IGNORE INTO file_table (uid,name,size,md5,node,created_at, updated_at) VALUES (#{uid},#{name},#{size},#{md5},#{node}, #{createdAt}, #{updatedAt})")
    int addRecord(@Param("uid") Integer uid,
                  @Param("name") String fileName,
                  @Param("size") Long size,
                  @Param("md5") String md5,
                  @Param("node") String nodeId,
                  @Param("createdAt") Date createdAt,
                  @Param("updatedAt") Date updatedAt
              );



    /**
     * 删除多条文件记录
     * @param uid 用户ID 0表示公共用户
     * @param node 文件路径ID
     * @param name 文件名
     * @return 受影响的行数
     */
    @Delete({
            "<script>",
                "DELETE FROM file_table WHERE node = #{node} AND name in ",
                "<foreach collection='name' item='item' open='(' separator=',' close=')'>",
                    "#{item}",
                "</foreach>",
            "AND uid=#{uid}",
            "</script>"
    })
    int deleteRecords(@Param("uid") Integer uid,
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
    int deleteRecord(@Param("uid") Integer uid,
                     @Param("node") String node,
                     @Param("name") String name);


    /**
     * 批量删除某个文件节点下的文件夹记录
     * @param uid    用户ID 0表示公共
     * @param nodes  节点ID列表
     * @return 删除数
     */
    @Delete({
            "<script>",
            "DELETE FROM file_table WHERE node in ",
                "<foreach collection='nodes' item='node' open='(' separator=',' close=')'>",
                    "#{node}",
                "</foreach>",
            " AND uid=#{uid} ",
            "</script>"
    })
    int deleteDirsRecord(@Param("uid") Integer uid,
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
    @Update("UPDATE file_table SET md5=#{newMd5}, size=#{newSize}, updated_at=NOW() WHERE node=#{node} AND uid=#{uid} AND name=#{name}")
    int updateRecord(@Param("uid") Integer uid,
                     @Param("name") String name,
                     @Param("node") String nodeId,
                     @Param("newSize") Long newSize,
                     @Param("newMd5") String newMd5);

    /**
     * 获取文件信息
     * @param uid    用户ID 0表示公共
     * @param name   文件名
     * @param nodeId 文件所在节点ID
     * @return 文件信息
     */
    @Select("SELECT name, size, md5, node FROM file_table WHERE node=#{nodeId} AND name=#{name} AND uid=#{uid} ")
    FileInfo getFileInfo(@Param("uid") Integer uid, @Param("name") String name, @Param("nodeId") String nodeId);

    /**
     * 批量获取某个目录下的文件信息
     * @param uid    用户ID 0表示公共
     * @param name   文件名列表
     * @param nodeId 路径ID
     * @return 删除数
     */
    @Select({
            "<script>",
            "SELECT name, size, md5, node AS parent FROM file_table WHERE node=#{nodeId} AND name in ",
                "<foreach collection='names' item='name' open='(' separator=',' close=')'>",
                    "#{name}",
                "</foreach>",
            " AND  uid=#{uid}",
            "</script>"
    })
    List<FileInfo> getFilesInfo(@Param("uid") Integer uid, @Param("names") Collection<String> name, @Param("nodeId") String nodeId);

    /**
     * 重命名文件或文件夹
     * @param uid   用户ID
     * @param nid   文件所在节点ID
     * @param oldName   旧文件名
     * @param newName   新文件名
     * @return  受影响的行数
     */
    @Update("UPDATE file_table SET name=#{newName} WHERE node=#{nid} AND name=#{oldName} AND uid=#{uid}")
    int rename(@Param("uid") Integer uid,
               @Param("nid") String nid,
               @Param("oldName") String oldName,
               @Param("newName") String newName);
}
