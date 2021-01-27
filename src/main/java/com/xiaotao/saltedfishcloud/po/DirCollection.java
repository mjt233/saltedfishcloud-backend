package com.xiaotao.saltedfishcloud.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.LinkedList;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DirCollection {
    private Long size = 0L;
    private Long dirsCount = 0L;
    private Long filesCount = 0L;
    private LinkedList<File> fileList = new LinkedList<>();
    private LinkedList<File> dirList = new LinkedList<>();
    public void addFile(File file) {
        if (file.isFile()) {
            size += file.length();
            fileList.addFirst(file);
            filesCount += 1;
        } else {
            dirsCount += 1;
            dirList.addFirst(file);
        }
    }
}
