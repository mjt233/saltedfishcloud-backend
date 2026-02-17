package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

/**
 * 存储整个目录树的结构，通常使用{@link com.xiaotao.saltedfishcloud.service.file.FileRecordService#getFullTree(long)}获取。<br>
 * 用于对大量数据的高速查询，避免频繁的数据库操作
 */
public class NodeTree implements Iterable<FileInfo> {

    private final Map<String, FileInfo> payload = new HashMap<>();

    /**
     * 添加一个节点信息到内部的存储库中，若节点ID重复则会覆盖原有信息
     * @param fileInfo 节点信息
     */
    public void putNode(FileInfo fileInfo) {
        payload.put(fileInfo.getMd5(), fileInfo);
    }

    /**
     * 从内部的存储库中通过节点ID获取对应的节点ID信息
     * @param nodeId    节点ID(FileInfo#getMd5)
     * @return  节点信息，若无对应数据则为null
     */
    public FileInfo getNode(String nodeId) {
        return payload.get(nodeId);
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
        FileInfo t;
        while ( !(id.length() < 32) && (t = payload.get(id)) != null ) {
            paths.addFirst(t.getName());
            id = t.getNode();
        }
        paths.forEach(e -> sb.append('/').append(e));
        return paths.isEmpty() ? null : sb.toString();
    }


    @Override
    public Iterator<FileInfo> iterator() {
        return new NodeTreeIterator(payload);
    }

}
