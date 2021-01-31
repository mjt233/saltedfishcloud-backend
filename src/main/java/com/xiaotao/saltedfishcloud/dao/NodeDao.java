package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.po.PathInfo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface NodeDao {
    @Insert("INSERT IGNORE INTO node_list (name, id, parent_id) VALUES (#{name}, #{id}, #{parent})")
    int addNode(@Param("name") String name, @Param("id") String id, @Param("parent") String parent);

    @Select("SELECT name,id,parent_id parent FROM node_list WHERE id=#{id}")
    NodeInfo getNodeById(@Param("id") String id);

    /**
     * 取某个用户目录下某个节点的所有直接子节点
     * @param uid   用户ID
     * @param nid   要查询的节点
     * @return  节点信息列表
     */
    @Select("SELECT name,id,parent_id parent FROM node_list " +
            "WHERE parent_id = #{nid} " +
            "AND " +
            "id IN " +
            "(SELECT node id FROM file_table WHERE uid=#{uid} GROUP BY node)")
    List<NodeInfo> getUserChildNodes(@Param("uid") Integer uid, @Param("nid") String nid);

    /**
     * 取某个用户目录下多个节点的所有直接子节点
     * @param uid   用户ID
     * @param nid   要查询的节点
     * @return  节点信息列表
     */
    @Select({
            "<script>",
            "SELECT name,id,parent_id parent FROM node_list ",
            "WHERE parent_id in ",
            "<foreach collection='nid' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "AND ",
            "id IN ",
            "(SELECT node id FROM file_table WHERE uid=#{uid} GROUP BY node)",
            "</script>"
    })
    List<NodeInfo> getUserChildNodesByMulti(@Param("uid") Integer uid, @Param("nid") Collection<String> nid );

    @Select("SELECT name,id,parent_id parent FROM node_list WHERE parent_id = #{pid} AND name = #{name}")
    NodeInfo getNodeByParentId(@Param("pid") String pid, @Param("name") String name);

}
