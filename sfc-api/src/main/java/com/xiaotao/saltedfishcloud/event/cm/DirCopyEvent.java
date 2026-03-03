package com.xiaotao.saltedfishcloud.event.cm;

public class DirCopyEvent extends CopyOrMoveEvent {
    public DirCopyEvent(Object source, Long sourceUid, String sourcePath, Long targetUid, String targetPath) {
        super(source, sourceUid, sourcePath, targetUid, targetPath);
    }
}
