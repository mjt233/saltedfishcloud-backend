package com.xiaotao.saltedfishcloud.dao.mybatis;

import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;

public interface NodeDao {

    /**
     * 获取用户的所有数据节点
     * @param uid   用户ID
     * @return      用户节点列表
     */
    @Select("SELECT name, id, parent, uid, mount_id FROM node_list WHERE uid = #{uid}")
    List<NodeInfo> getAllNode(@Param("uid") Long uid);

    /**
     * 将节点移动到另一个节点下
     * @param uid       用户ID
     * @param nodeId    被移动的节点ID
     * @param parentId  移动目的地节点ID
     * @return  受影响行数
     */
    @Update("UPDATE node_list SET parent=#{pid} WHERE id=#{nid} AND uid=#{uid}")
    int move(@Param("uid") Long uid, @Param("nid") String nodeId, @Param("pid") String parentId);

    /**
     * 通过节点ID获取节点详细信息
     * @param uid   用户ID
     * @param nodeId    节点ID
     * @return  节点信息或null
     */
    @Select("SELECT name,id,parent,uid FROM node_list WHERE id=#{nodeId} AND uid=#{uid}")
    NodeInfo getNodeById(@Param("uid") Long uid,
                         @Param("nodeId") String nodeId);

    /**
     * 插入一个节点
     * @param uid   用户ID
     * @param name  节点名称
     * @param id    节点ID
     * @param parent    父节点
     * @return  插入的行数
     */
    @Insert("INSERT INTO node_list (name, id, parent, uid, is_mount) VALUES (#{name}, #{id}, #{parent}, #{uid}, #{isMount})")
    int addNode(@Param("uid") Long uid,
                @Param("name") String name,
                @Param("id") String id,
                @Param("parent") String parent,
                @Param("isMount") Boolean isMount
    );


    /**
     * 通过父节点ID获取某个目录节点信息
     * @param uid   用户ID
     * @param pid   父节点ID
     * @param name  目标节点名称
     * @return  节点信息
     */
    @Select("SELECT name, id, parent, uid, parent, mount_id FROM node_list WHERE parent = #{pid} AND name = #{name} AND  uid = #{uid}")
    NodeInfo getNodeByParentId(@Param("uid") Long uid, @Param("pid") String pid, @Param("name") String name);


    /**
     * 修改节点的父节点
     * @param uid   用户ID
     * @param nid   节点ID
     * @param parent    新的父节点ID
     * @return 影响的行数
     */
    @Update("UPDATE node_list SET parent=#{parent} WHERE id=#{nid} AND uid=#{uid}")
    int changeParent(@Param("uid") Long uid, @Param("nid") String nid, @Param("parent") String parent);

    /**
     * 修改节点的名称
     * @param uid   用户ID
     * @param nid   节点ID
     * @param name  新的节点名称
     * @return 影响的行数
     */
    @Update("UPDATE node_list SET name=#{name} WHERE id=#{nid} AND uid=#{uid}")
    int changeName(@Param("uid") Long uid, @Param("nid") String nid, @Param("name") String name);

    /**
     * 修改节点的父节点和名称
     * @param uid   用户ID
     * @param nid   节点ID
     * @param parent    新的父节点ID
     * @param name   新的节点名称
     * @return 影响的行数
     */
    @Update("UPDATE node_list SET name=#{name}, parent=#{parent} WHERE id=#{nid} AND uid=#{uid}")
    int changNode(@Param("uid") Long uid, @Param("nid") String nid,@Param("parent") String parent, @Param("name") String name);
}
