package com.xiaotao.saltedfishcloud.compress.reader.impl;

import com.xiaotao.saltedfishcloud.compress.reader.CompressFile;
import com.xiaotao.saltedfishcloud.compress.utils.CharacterUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;

@Slf4j
public class CompressFileImpl implements CompressFile {
    private final ArchiveEntry zipEntry;
    private String path;
    private String name;
    public CompressFileImpl(ArchiveEntry entry) {
        this.zipEntry = entry;
    }

    public String getName() {
        // 缓存的文件名，存在则直接返回（因为文件名需要从完整路径中解析，需要避免重复运算和不必要的运算，懒加载思想）
        if (this.name != null) return this.name;

        // 未获取过，从path中解析出文件名


        String path = getPath();
        int pos = path.lastIndexOf('/');
        if (pos == -1) {

            // 不存在/，则说明是根目录下的文件（如：富婆通讯录.xlsx）
            this.name = path;
        } else if (pos == path.length() - 1){

            // /在末尾，是个文件夹，找第二个/出现的位置以截取文件名
            int p2 = path.lastIndexOf('/', path.length() - 2);
            if (p2 == -1) {

                // 找不到第二个/说明是个在根目录下的文件夹（如：新建文件夹/）
                this.name = path.substring(0, path.length() - 1);
            } else {

                // 找到了（如：新建文件夹/子文件夹/
                this.name = path.substring(pos + 1, path.length() - 1);
            }
        } else {

            // 存在/但不是在末尾，普通的文件，如（文件夹/文档.doc）
            this.name = path.substring(pos + 1);
        }
        return this.name;
    }

    public boolean isDirectory() {
        return zipEntry.isDirectory();
    }

    public long getSize() {
        return zipEntry.getSize();
    }

    public String getPath() {
        if (this.path != null) return this.path;
        this.path = zipEntry.getName();
        if (CharacterUtils.isMessyCode(this.path)) {
            throw new UnsupportedOperationException("Just support GBK charset because I don't how to compatible with different charset :( ");
        }
        return this.path;
    }

    @Override
    public String toString() {
        return "ZipCompressFile{" +
                "name=" + getName() +
                '}';
    }
}
