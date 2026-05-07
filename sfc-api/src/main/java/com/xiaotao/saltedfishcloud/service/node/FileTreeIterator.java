package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;

import java.util.Iterator;
import java.util.Map;

/**
 * 节点树迭代器，<strong>无序</strong>取出节点信息
 */
public class FileTreeIterator implements Iterator<FileInfo> {
    private final Map<String, FileInfo> payload;
    private final Iterator<String> keyIterator;
    FileTreeIterator(Map<String, FileInfo> data) {
        this.payload = data;
        keyIterator = data.keySet().iterator();
    }

    @Override
    public boolean hasNext() {
        return keyIterator.hasNext();
    }

    @Override
    public FileInfo next() {
        return payload.get(keyIterator.next());
    }

}
