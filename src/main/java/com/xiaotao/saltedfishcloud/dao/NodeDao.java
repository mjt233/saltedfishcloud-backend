package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.po.PathInfo;
import org.apache.ibatis.annotations.*;
import org.springframework.security.core.parameters.P;

import java.util.Collection;
import java.util.List;

public interface NodeDao {
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
    @Update("UPDATE node_list SET parent=#{parent} WHERE nid=#{nid} AND uid=#{uid}")
    int changeParent(@Param("uid") Integer uid, @Param("nid") String nid, @Param("parent") String parent);

    /**
     * 修改节点的名称
     * @param uid   用户ID
     * @param nid   节点ID
     * @param name  节点名称
     * @return 影响的行数
     */
    @Update("UPDATE node_list SET name=#{name} WHERE nid=#{nid} AND uid=#{uid}")
    int changeName(@Param("uid") Integer uid, @Param("nid") String nid, @Param("name") String name);

    /**
     * 修改节点的父节点和名称
     * @param uid   用户ID
     * @param nid   节点ID
     * @param parent    新的父节点ID
     * @param name   新的节点名称
     * @return 影响的行数
     */
    @Update("UPDATE node_list SET name=#{name}, parent=#{parent} WHERE nid=#{nid} AND uid=#{uid}")
    int changNode(@Param("uid") Integer uid, @Param("nid") String nid,@Param("parent") String parent, @Param("name") String name);
}
