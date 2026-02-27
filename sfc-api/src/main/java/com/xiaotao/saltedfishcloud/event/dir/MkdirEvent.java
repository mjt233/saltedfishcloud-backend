package com.xiaotao.saltedfishcloud.event.dir;

import org.springframework.context.ApplicationEvent;

/**
 * 创建文件夹事件(由于父目录不存在而自动创建的目录不会触发该事件)
 */
public class MkdirEvent extends ApplicationEvent {
    /**
     * 用户id
     */
    private Long uid;

    /**
     * 新创建的文件夹路径
     */
    private String path;

    /**
     * 新创建的文件夹节点id
     */
    private String nodeId;

    public MkdirEvent(Object source, Long uid, String path, String nodeId) {
        super(source);
    }
}
