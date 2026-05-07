package com.xiaotao.saltedfishcloud.event.file;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;

import java.util.function.Supplier;

/**
 * 文件重命名事件(由于文件的上级目录被删除时，不会触发该事件)
 */
public class FileRenameEvent extends FileEvent {
    public FileRenameEvent(Object source, Long uid, String fullPath, Supplier<FileInfo> fileInfoSupplier) {
        super(source, uid, fullPath, fileInfoSupplier);
    }
}
