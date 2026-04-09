package com.xiaotao.saltedfishcloud.event.cm;

/**
 * 复制事件（文件或目录）
 */
public class FileCopyEvent extends CopyOrMoveEvent {
    public FileCopyEvent(Object source, String sourcePath, String targetPath) {
        super(source, sourcePath, targetPath);
    }
}
