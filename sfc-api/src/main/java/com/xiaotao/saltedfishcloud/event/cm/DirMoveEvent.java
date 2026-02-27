package com.xiaotao.saltedfishcloud.event.cm;

public class DirMoveEvent extends CopyOrMoveEvent {
    public DirMoveEvent(Object source, Long sourceUid, String sourcePath, Long targetUid, String targetPath) {
        super(source, sourceUid, sourcePath, targetUid, targetPath);
    }
}
