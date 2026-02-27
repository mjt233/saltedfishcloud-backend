package com.xiaotao.saltedfishcloud.event.cm;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文件或目录复制事件
 */
@Getter
public abstract class CopyOrMoveEvent extends ApplicationEvent {
    /**
     * 源文件用户id
     */
    private final Long sourceUid;

    /**
     * 复制的源文件完整路径
     */
    private final String sourcePath;

    /**
     * 目标文件用户id
     */
    private final Long targetUid;

    /**
     * 复制到的目标文件完整路径
     */
    private final String targetPath;

    public CopyOrMoveEvent(Object source, Long sourceUid, String sourcePath, Long targetUid, String targetPath) {
        super(source);
        this.sourceUid = sourceUid;
        this.sourcePath = sourcePath;
        this.targetUid = targetUid;
        this.targetPath = targetPath;
    }
}
