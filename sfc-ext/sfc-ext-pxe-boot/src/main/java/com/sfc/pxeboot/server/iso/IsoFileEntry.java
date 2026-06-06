package com.sfc.pxeboot.server.iso;

import lombok.Getter;

/**
 * ISO 文件条目元数据。
 * <p>路径字段遵循规范：以 {@code /} 开头，除根目录 {@code /} 外不以 {@code /} 结尾。</p>
 */
@Getter
public class IsoFileEntry {
    /** 文件名 */
    private final String name;
    /** ISO 内完整路径（如 "/boot/vmlinuz"，根目录为 "/"） */
    private final String path;
    /** 文件大小（目录为 -1 或 0） */
    private final long size;
    /** 最后修改时间（epoch 毫秒） */
    private final long lastModified;
    /** 条目类型 */
    private final EntryType type;

    /**
     * 构造函数。
     *
     * @param name         文件名
     * @param path         ISO 内完整路径（如 "/boot/vmlinuz"）
     * @param size         文件大小
     * @param lastModified 最后修改时间（epoch 毫秒）
     * @param type         条目类型
     */
    public IsoFileEntry(String name, String path, long size, long lastModified, EntryType type) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.lastModified = lastModified;
        this.type = type;
    }

    /**
     * 判断是否为文件。
     *
     * @return 是文件返回 true
     */
    public boolean isFile() {
        return type == EntryType.FILE;
    }

    /**
     * 判断是否为目录。
     *
     * @return 是目录返回 true
     */
    public boolean isDir() {
        return type == EntryType.DIRECTORY;
    }

    /**
     * 条目类型枚举。
     */
    public enum EntryType {
        /** 文件 */
        FILE,
        /** 目录 */
        DIRECTORY
    }
}
