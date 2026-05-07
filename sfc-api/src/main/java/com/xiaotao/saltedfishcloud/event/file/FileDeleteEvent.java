package com.xiaotao.saltedfishcloud.event.file;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collection;

/**
 * 文件被删除的事件(由于文件的上级目录被删除时，不会触发该事件)
 */
public class FileDeleteEvent extends ApplicationEvent {
    /**
     * 被删除的文件的所属用户 id
     */
    @Getter
    private final Long uid;

    /**
     * 被删除的文件的所在目录路径
     */
    @Getter
    private final String parentPath;

    /**
     * 在{@link #getParentPath()}下的被删除的文件名
     */
    @Getter
    private final Collection<String> names;
    public FileDeleteEvent(Object source, Long uid, String parentPath, Collection<String> names) {
        super(source);
        this.uid = uid;
        this.parentPath = parentPath;
        this.names = names;
    }
}
