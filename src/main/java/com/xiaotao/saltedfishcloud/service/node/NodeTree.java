package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.po.NodeInfo;

import java.util.*;

/**
 * 存储整个目录树的结构，通常使用{@link NodeService}的getFullTree方法获取。<br>
 * 用于对大量数据的高速查询，避免频繁的数据库操作
 */
public class NodeTree implements Iterable<NodeInfo> {

    private final Map<String, NodeInfo> payload = new HashMap<>();

    /**
     * 添加一个节点信息到内部的存储库中，若节点ID重复则会覆盖原有信息
     * @param nodeInfo 节点信息
     */
    public void putNode(NodeInfo nodeInfo) {
        payload.put(nodeInfo.getId(), nodeInfo);
    }

    /**
     * 从内部的存储库中通过节点ID获取对应的节点ID信息
     * @param id    节点ID
     * @return  节点信息，若无对应数据则为null
     */
    public NodeInfo getNode(String id) {
        return payload.get(id);
    }

    /**
     * 获取节点id的完整路径<br>
     * 若id不存在，则返回null
     * @param id    节点ID
     * @return      路径
     */
    public String getPath(String id) {
        if (id.length() < 32) {
            return "/";
        }
        LinkedList<String> paths = new LinkedList<>();
        StringBuilder sb = new StringBuilder();
        NodeInfo t;
        while ( !(id.length() < 32) && (t = payload.get(id)) != null ) {
            paths.addFirst(t.getName());
            id = t.getParent();
        }
        paths.forEach(e -> sb.append('/').append(e));
        return paths.size() == 0 ? null : sb.toString();
    }


    @Override
    public Iterator<NodeInfo> iterator() {
        return new NodeTreeIterator(payload);
    }

}
