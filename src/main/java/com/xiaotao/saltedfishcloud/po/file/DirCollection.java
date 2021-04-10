package com.xiaotao.saltedfishcloud.po.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.LinkedList;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DirCollection {
    private long itemCount = 0L;
    private long size = 0L;
    private long dirsCount = 0L;
    private long filesCount = 0L;
    private LinkedList<File> fileList = new LinkedList<>();
    private LinkedList<File> dirList = new LinkedList<>();
    public void addFile(File file) {
        ++itemCount;
        if (file.isFile()) {
            size += file.length();
            fileList.addFirst(file);
            filesCount += 1;
        } else {
            dirsCount += 1;
            dirList.addFirst(file);
        }
    }

    /**
     * 取所有文件总大小
     * @return 文件总大小 单位字节
     */
    public long getSize() {
        return size;
    }

    /**
     * 取所有文件和文件夹的总数量
     * @return 文件总数量
     */
    public long getItemCount() {
        return itemCount;
    }
}
