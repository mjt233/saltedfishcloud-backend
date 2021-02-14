package com.xiaotao.saltedfishcloud.helper;

import lombok.Data;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用于构建URL
 */
@Data
public class PathBuilder {
    private LinkedList<String> path;
    private boolean prefix = true;


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


    /**
     * 对路径进行格式化 去除重复或末尾的的/或\
     * @param path 输入路径
     * @param prefix 前缀
     * @return 标准化后的路径
     */
    public static String formatPath(String path, boolean prefix) {
        PathBuilder pb = new PathBuilder();
        pb.setPrefix(prefix);
        return pb.append(path).toString();
    }

    /**
     * 对路径进行格式化 去除重复或末尾的的/或\
     * @param path 输入路径
     * @return 标准化后的路径
     */
    public static String formatPath(String path) {
        return formatPath(path, true);
    }

    /**
     * 清空
     * @return 自己
     */
    public PathBuilder clear() {
        path.clear();
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        AtomicBoolean first = new AtomicBoolean(true);
        path.forEach(node -> {
            if (prefix || !first.get()) {
                sb.append("/");
            }
            sb.append(node);
            first.set(false);
        });
        if (sb.length() == 0) {
            return "/";
        } else {
            return sb.toString();
        }
    }
}
