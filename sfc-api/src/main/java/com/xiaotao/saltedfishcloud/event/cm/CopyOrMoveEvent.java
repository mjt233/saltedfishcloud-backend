package com.xiaotao.saltedfishcloud.event.cm;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文件或目录复制/移动事件
 */
@Getter
public abstract class CopyOrMoveEvent extends ApplicationEvent {
    /**
     * 源文件完整路径
     */
    private final String sourcePath;

    /**
     * 目标文件完整路径
     */
    private final String targetPath;

    public CopyOrMoveEvent(Object source, String sourcePath, String targetPath) {
        super(source);
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
    }
}
