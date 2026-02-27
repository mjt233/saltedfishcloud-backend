package com.xiaotao.saltedfishcloud.event.file;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.function.Supplier;


/**
 * 文件删除、移动、重命名事件
 */
public abstract class FileEvent extends ApplicationEvent {
    public volatile FileInfo fileInfo;
    private final Supplier<FileInfo> fileInfoSupplier;

    @Getter
    private final Long uid;

    @Getter
    private final String fullPath;

    public FileEvent(Object source, Long uid, String fullPath, Supplier<FileInfo> fileInfoSupplier) {
        super(source);
        this.fileInfoSupplier = fileInfoSupplier;
        this.uid = uid;
        this.fullPath = fullPath;
    }

    public FileInfo getFileInfo() {
        if (fileInfo != null) {
            return fileInfo;
        }
        synchronized (this) {
            if (fileInfo != null) {
                return fileInfo;
            }
            fileInfo = fileInfoSupplier.get();
        }
        return fileInfo;
    }
}
