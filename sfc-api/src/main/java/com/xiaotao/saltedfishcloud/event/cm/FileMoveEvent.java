package com.xiaotao.saltedfishcloud.event.cm;

/**
 * 移动事件（文件或目录）
 */
public class FileMoveEvent extends CopyOrMoveEvent {
    public FileMoveEvent(Object source, String sourcePath, String targetPath) {
        super(source, sourcePath, targetPath);
    }
}
