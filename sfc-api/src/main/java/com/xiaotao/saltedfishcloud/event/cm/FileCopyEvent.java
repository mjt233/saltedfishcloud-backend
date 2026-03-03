package com.xiaotao.saltedfishcloud.event.cm;

public class FileCopyEvent extends CopyOrMoveEvent {
    public FileCopyEvent(Object source, Long sourceUid, String sourcePath, Long targetUid, String targetPath) {
        super(source, sourceUid, sourcePath, targetUid, targetPath);
    }
}
