package com.xiaotao.saltedfishcloud.event.cm;

public class FileMoveEvent extends CopyOrMoveEvent {
    public FileMoveEvent(Object source, Long sourceUid, String sourcePath, Long targetUid, String targetPath) {
        super(source, sourceUid, sourcePath, targetUid, targetPath);
    }
}
