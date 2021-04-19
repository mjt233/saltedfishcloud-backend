package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.po.NodeInfo;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;

public interface NodeDao {

    /**
     * 将节点移动到另一个节点下
     * @param uid       用户ID
     * @param nodeId    被移动的节点ID
     * @param parentId  移动目的地节点ID
     * @return  受影响行数
     */
    @Update("UPDATE node_list SET parent=#{pid} WHERE uid=#{uid} AND id=#{nid}")
    int move(@Param("uid") Integer uid, @Param("nid") String nodeId, @Param("pid") String parentId);

    /**
     * 通过节点ID获取节点详细信息
     * @param uid   用户ID
     * @param nodeId    节点ID
     * @return  节点信息或null
     */
    @Select("SELECT name,id,parent,uid FROM node_list WHERE uid=#{uid} AND id=#{nodeId}")
    NodeInfo getNodeById(@Param("uid") Integer uid,
                         @Param("nodeId") String nodeId);

    /**
     * 插入一个节点
     * @param uid   用户ID
     * @param name  节点名称
     * @param id    节点ID
     * @param parent    父节点
     * @return  插入的行数
     */
    @Insert("INSERT IGNORE INTO node_list (name, id, parent, uid) VALUES (#{name}, #{id}, #{parent}, #{uid})")
    int addNode(@Param("uid") Integer uid,
                @Param("name") String name,
                @Param("id") String id,
                @Param("parent") String parent);

    /**
     * 取某个用户目录下多个节点的所有直接子节点
     * @param uid   用户ID
     * @param nid   要查询的节点
     * @return  节点信息列表
     */
    @Select({
            "<script>",
            "SELECT name, id, parent, uid FROM node_list ",
            "WHERE uid = #{uid} AND parent in ",
                "<foreach collection='nid' item='id' open='(' separator=',' close=')'>",
                "#{id}",
                "</foreach>",
            "</script>"
    })
    List<NodeInfo> getChildNodes(@Param("uid") Integer uid, @Param("nid") Collection<String> nid );

    /**
     * 通过父节点ID获取某个目录节点信息
     * @param uid   用户ID
     * @param pid   父节点ID
     * @param name  目标节点名称
     * @return  节点信息
     */
    @Select("SELECT name, id, parent, uid parent FROM node_list WHERE uid = #{uid} AND parent = #{pid} AND name = #{name}")
    NodeInfo getNodeByParentId(@Param("uid") Integer uid, @Param("pid") String pid, @Param("name") String name);

    @Delete({
            "<script>",
            "DELETE FROM node_list WHERE uid=#{uid} AND id IN ",
                "<foreach collection='nodes' item='node' open='(' separator=',' close=')'>",
                "#{node}",
                "</foreach>",
            "</script>"
    })
    int deleteNodes(@Param("uid") Integer uid, @Param("nodes") Collection<String> nodes);

    /**
     * 修改节点的父节点
     * @param uid   用户ID
     * @param nid   节点ID
     * @param parent    新的父节点ID
     * @return 影响的行数
     */
    @Update("UPDATE node_list SET parent=#{parent} WHERE id=#{nid} AND uid=#{uid}")
    int changeParent(@Param("uid") Integer uid, @Param("nid") String nid, @Param("parent") String parent);

    /**
     * 修改节点的名称
     * @param uid   用户ID
     * @param nid   节点ID
     * @param name  新的节点名称
     * @return 影响的行数
     */
    @Update("UPDATE node_list SET name=#{name} WHERE id=#{nid} AND uid=#{uid}")
    int changeName(@Param("uid") Integer uid, @Param("nid") String nid, @Param("name") String name);

    /**
     * 修改节点的父节点和名称
     * @param uid   用户ID
     * @param nid   节点ID
     * @param parent    新的父节点ID
     * @param name   新的节点名称
     * @return 影响的行数
     */
    @Update("UPDATE node_list SET name=#{name}, parent=#{parent} WHERE id=#{nid} AND uid=#{uid}")
    int changNode(@Param("uid") Integer uid, @Param("nid") String nid,@Param("parent") String parent, @Param("name") String name);
}
