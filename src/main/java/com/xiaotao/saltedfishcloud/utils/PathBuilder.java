package com.xiaotao.saltedfishcloud.utils;

import java.util.Collection;
import java.util.LinkedList;

/**
 * 用于构建URL
 */
public class PathBuilder {
    private LinkedList<String> path;

    public PathBuilder() {
        path = new LinkedList<>();
    }

    /**
     * 获取路径位置集合
     * @return 目录路径集合
     */
    public Collection<String> getPath() {
        return path;
    }

    /**
     * 尾部插入一个或多个节点
     * @param path 节点名称或一个路径，使用/或\均可
     * @return 自己
     */
    public PathBuilder append(String path) {
        String[] split = path.split("(/+|\\\\+)");
        for (String node:
             split) {
            switch (node) {
                case ".":
                case "":
                    continue;
                case "..":
                    this.path.removeLast();
                    break;
                default:
                    this.path.addLast(node);
            }
        }
        return this;
    }

    public PathBuilder clear() {
        path.clear();
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        path.forEach(node -> {
            sb.append("/").append(node);
        });
        return sb.toString();
    }
}
