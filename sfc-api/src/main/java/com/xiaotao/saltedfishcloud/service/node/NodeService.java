package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import org.jetbrains.annotations.Nullable;

import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public interface NodeService {
    /**
     * 根据用户ID和节点ID获取对应的节点信息，可识别到根ID
     * @param uid   用户ID
     * @param nid   节点ID，可以是根ID
     * @return      节点ID，若无结果则为null
     */
    NodeInfo getNodeById(Long uid, String nid);

    /**
     * 获取用户的完整目录树
     * @param uid   用户ID
     * @return      节点目录树
     */
    NodeTree getFullTree(long uid);

    /**
     * 根据路径获取对应的节点对象
     * @param uid   用户id
     * @param path  路径
     * @return      若路径不存在则返回null
     */
    @Nullable
    NodeInfo getNodeByPath(long uid, String path);

    /**
     * 按节点id列出节点下面的直接一级节点
     * @param uid       用户id
     * @param nodeId    开始遍历的节点id
     * @return          若节点id不存在则返回null
     */
    @Nullable
    List<NodeInfo> listNodeByNodeId(long uid, String nodeId);

    /**
     * 根据节点id取出节点的所有父节点
     * @param uid       用户id
     * @param nodeId    查找的节点id
     * @return          所有父节点
     */
    List<NodeInfo> listAllParentByNodeId(long uid, String nodeId);

    NodeInfo getNodeByParentId(long uid, String parentId, String nodeName);

    /**
     * 获取某个路径中途径的节点信息<br>
     *  <b>注意：</b>若路径不存在抛出异常可能会导致事务中断后不可再操作数据库
     * @param uid   用户ID
     * @param path  路径
     * @throws NoSuchFileException 请求的路径不存在时抛出此异常
     * @return  节点信息列表
     */
    Deque<NodeInfo> getPathNodeByPath(long uid, String path) throws NoSuchFileException;

    /**
     * 获取某个路径中途径的节点信息，若路径不存在则返回null而不是抛出异常
     * @param uid   用户ID
     * @param path  路径
     * @return  节点信息列表
     */
    Deque<NodeInfo> getPathNodeByPathNoEx(long uid, String path);

    /**
     * 添加一个节点
     * @param uid 用户ID
     * @param name 名称
     * @param parent 父节点ID
     * @return 新节点ID
     */
    String addNode(long uid, String name, String parent);

    /**
     * 添加一个节点
     * @param uid 用户ID
     * @param name 名称
     * @param parent 父节点ID
     * @param isMount 是否为挂载点节点
     * @return 新节点ID
     */
    String addNode(long uid, String name, String parent, Boolean isMount);

    /**
     * 取某节点下的所有子节点
     * @param uid 用户ID
     * @param nid 目标节点ID
     * @return 目标节点下的所有子节点（不包含自己）
     */
    List<NodeInfo> getChildNodes(long uid, String nid);

    /**
     * 移除节点
     * @param uid   用户ID
     * @param ids   节点ID集合
     * @return  删除数
     */
    int deleteNodes(long uid, Collection<String> ids);

    /**
     * 获取路径对应的节点ID<br>
     * <b>注意：</b>若路径不存在抛出异常可能会导致事务中断后不可再操作数据库
     * @param uid   用户ID
     * @param path  请求的路径
     * @return  节点ID
     * @throws NoSuchFileException  路径不存在
     */
    String getNodeIdByPath(long uid, String path) throws NoSuchFileException;

    /**
     * 获取路径对应的节点ID，若路径不存在则返回null而不是抛出异常
     * @param uid   用户ID
     * @param path  请求的路径
     * @return  节点ID
     */
    String getNodeIdByPathNoEx(long uid, String path);

    /**
     * 通过节点ID 获取节点所在的完整路径位置。
     * @param uid       用户ID
     * @param nodeId    节点ID
     * @return          完整路径
     */
    String getPathByNode(long uid, String nodeId);

    /**
     * 添加挂载点节点
     * @param mountPoint    挂载点
     * @return 新节点id
     */
    String addMountPointNode(MountPoint mountPoint);
}
