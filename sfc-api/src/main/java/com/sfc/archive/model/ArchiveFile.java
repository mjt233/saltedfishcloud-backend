package com.sfc.archive.model;

import com.sfc.archive.utils.ArchiveUtils;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.compress.archivers.ArchiveEntry;

/**
 * 压缩文件信息
 */
public abstract class ArchiveFile {
    private String name;

    /**
     * 文件创建日期
     */
    @Setter
    @Getter
    private Long ctime;

    /**
     * 文件修改日期
     */
    @Setter
    @Getter
    private Long mtime;

    /**
     * 获取文件名（而不是完整路径+文件名）
     */
    public String getName() {
        // 缓存的文件名，存在则直接返回（因为文件名需要从完整路径中解析，需要避免重复运算和不必要的运算，懒加载思想）
        if (this.name != null) return this.name;
        this.name = ArchiveUtils.getFilename(this.getPath());
        return this.name;
    }

    /**
     * 判断是否为目录
     */
    public boolean isDirectory() {
        return getPath().endsWith("/");
    }

    /**
     * 获取文件大小
     */
    public abstract long getSize();

    /**
     * 获取文件在压缩文件中的完整路径+文件名 <br>
     * todo 识别编码不一致导致的文件名乱码还原
     */
    public abstract String getPath();

    public static ArchiveFile formArchiveEntry(ArchiveEntry entry) {
        if (entry == null) throw new NullPointerException();
        return new CommonArchiveFile(entry);
    }
}
