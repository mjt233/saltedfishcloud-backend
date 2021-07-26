package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.po.NodeInfo;

import java.util.Iterator;
import java.util.Map;

/**
 * 节点树迭代器，<strong>无序</strong>取出节点信息
 */
public class NodeTreeIterator implements Iterator<NodeInfo> {
    private final Map<String, NodeInfo> payload;
    private final Iterator<String> keyIterator;
    NodeTreeIterator(Map<String, NodeInfo> data) {
        this.payload = data;
        keyIterator = data.keySet().iterator();
    }

    @Override
    public boolean hasNext() {
        return keyIterator.hasNext();
    }

    @Override
    public NodeInfo next() {
        return payload.get(keyIterator.next());
    }

}
