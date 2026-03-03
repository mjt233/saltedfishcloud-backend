package com.xiaotao.saltedfishcloud.event.file;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;

import java.util.function.Supplier;

/**
 * 文件保存事件，单独保存一个文件或秒传成功时触发。复制文件夹时连带复制的文件不会触发。
 */
public class FileStoreEvent extends FileEvent {
    public FileStoreEvent(Object source, Long uid, String fullPath, Supplier<FileInfo> fileInfoSupplier) {
        super(source, uid, fullPath, fileInfoSupplier);
    }
}
