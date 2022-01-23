package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.entity.po.NodeInfo;

import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public interface NodeService {
    /**
     * 根据用户ID和节点ID获取对应的节点信息，可识别到根ID
     * @param uid   用户ID
     * @param nid   节点ID，可以是根ID
     * @return      节点ID，若无结果则为null
     */
    NodeInfo getNodeById(Integer uid, String nid);

    /**
     * 获取用户的完整目录树
     * @param uid   用户ID
     * @return      节点目录树
     */
    NodeTree getFullTree(int uid);

    NodeInfo getNodeByParentId(int uid, String parentId, String nodeName);

    /**
     * 获取某个路径中途径的节点信息
     * @param uid   用户ID
     * @param path  路径
     * @throws NoSuchFileException 请求的路径不存在时抛出此异常
     * @return  节点信息列表
     */
    LinkedList<NodeInfo> getPathNodeByPath(int uid, String path) throws NoSuchFileException;

    /**
     * 添加一个节点
     * @param uid 用户ID
     * @param name 名称
     * @param parent 父节点ID
     * @return 新节点ID
     */
    String addNode(int uid, String name, String parent);

    /**
     * 取某节点下的所有子节点
     * @param uid 用户ID
     * @param nid 目标节点ID
     * @return 目标节点下的所有子节点（不包含自己）
     */
    List<NodeInfo> getChildNodes(int uid, String nid);

    /**
     * 移除节点
     * @param uid   用户ID
     * @param ids   节点ID集合
     * @return  删除数
     */
    int deleteNodes(int uid, Collection<String> ids);

    /**
     * 获取路径对应的节点ID
     * @param uid   用户ID
     * @param path  请求的路径
     * @return  节点ID
     * @throws NoSuchFileException  路径不存在
     */
    String getNodeIdByPath(int uid, String path) throws NoSuchFileException;

    /**
     * 通过节点ID 获取节点所在的完整路径位置
     * @param uid       用户ID
     * @param nodeId    节点ID
     * @return          完整路径
     */
    String getPathByNode(int uid, String nodeId);
}
