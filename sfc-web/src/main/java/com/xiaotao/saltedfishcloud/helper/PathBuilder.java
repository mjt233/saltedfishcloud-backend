package com.xiaotao.saltedfishcloud.helper;

import com.xiaotao.saltedfishcloud.utils.OSInfo;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * 用于构建URL
 */
@Data
public class PathBuilder {
    private LinkedList<String> path;
    private boolean forcePrefix = false;

    @Setter(AccessLevel.NONE)
    private String cacheString;


    public PathBuilder() {
        path = new LinkedList<>();
    }

    /**
     * 清空路径信息并设置新的路径信息 <br> 等同于依次执行 {@link #clear()} 和 {@link #append(String)}
     * @param path  路径字符串
     * @return  自己
     */
    public PathBuilder update(String path) {
        this.path.clear();
        append(path);
        return this;
    }

    /**
     * 在已有路径信息中获取从起始位置开始的指定节点长度的路径字符串
     * @see #range(int, int)
     * @param length 若参数{@code length}为负数，则表示忽略末尾的长度
     * @return 咸鱼云网盘标准格式化路径
     */
    public String range(int length) {
        return range(length, 0);
    }

    /**
     * 从已有路径信息中获取指定节点长度的路径字符串，若参数{@code length}为负数，则表示忽略末尾的长度
     * @param length
     *        节点长度，若为负数，则表示丢弃的末尾节点数。 <br>
     *        如节点长度为5，参数length为-2时，则返回前3个节点的咸鱼云网盘标准格式化路径 <br>
     *
     * @param index
     *        起始索引，若为负数，则表示从末尾开始往前计数
     *
     * @throws IndexOutOfBoundsException
     *     当传入的length大于从指定的index到末尾的长度时抛出此异常
     *
     *
     * @return 咸鱼云网盘标准格式化路径
     */
    public String range(int length, int index) {
        int finalIndex = index < 0 ? this.path.size() + index : index;
        int finalLength = length < 0 ? this.path.size() + length : length;


        StringBuilder sb = new StringBuilder();
        boolean first = true;
        int cnt = 0;

        Iterator<String> iterator = this.path.listIterator(finalIndex);
        while (iterator.hasNext()) {
            if (++cnt > finalLength) break;
            if (forcePrefix ||
                    ( !first || !OSInfo.isWindows())
            )sb.append("/");

            sb.append(iterator.next());

            first = false;
        }

        if (sb.length() == 0) {
            return "/";
        } else {
            return sb.toString();
        }
    }

    /**
     * 获取路径位置集合
     * @return 目录路径集合
     */
    public LinkedList<String> getPath() {
        return path;
    }

    /**
     * 尾部插入一个或多个节点
     * @param path 节点名称或一个路径，使用/或\均可
     * @return 自己
     */
    public PathBuilder append(String path) {
        String[] split = path.split("(/+|\\\\+)");
        cacheString = null;
        for (String node:
             split) {
            switch (node) {
                case ".":
                case "":
                    continue;
                case "..":
                    if (this.path.size() != 0) {
                        this.path.removeLast();
                    }
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
     * @param prefix 是否使用/或\作为根节点
     * @return 标准化后的路径
     */
    public static String formatPath(String path, boolean prefix) {
        PathBuilder pb = new PathBuilder();
        pb.setForcePrefix(prefix);
        return pb.append(path).toString();
    }

    /**
     * 对路径进行格式化 去除重复或末尾的的/或\
     * @param path 输入路径
     * @return 标准化后的路径
     */
    public static String formatPath(String path) {
        if (OSInfo.isWindows()) {
            return formatPath(path, false);
        } else {
            return formatPath(path, true);
        }
    }

    /**
     * 清空
     * @return 自己
     */
    public PathBuilder clear() {
        path.clear();
        cacheString = null;
        return this;
    }

    @Override
    public String toString() {
        if (cacheString == null) {
            cacheString = range(path.size());
        }
        return cacheString;
    }
}
