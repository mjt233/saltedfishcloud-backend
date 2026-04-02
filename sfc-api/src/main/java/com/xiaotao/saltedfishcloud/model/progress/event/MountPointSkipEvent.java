package com.xiaotao.saltedfishcloud.model.progress.event;

import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEvent;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEventLevel;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;

/**
 * 挂载点跳过事件
 * <p>当复制操作遇到挂载点时，挂载点本身及其下的文件会被跳过复制</p>
 */
public class MountPointSkipEvent extends CopyProgressEvent {

    /**
     * 事件名称常量
     */
    public static final String NAME = "mount_point_skip";

    public MountPointSkipEvent() {
        super();
        setName(NAME);
        setLevel(CopyProgressEventLevel.WARN);
    }

    /**
     * 创建挂载点跳过事件
     * @param mountPointName 挂载点名称
     * @param from 源路径
     * @param to 目标路径
     * @return 事件实例
     */
    public static MountPointSkipEvent of(String mountPointName, String from, String to) {
        MountPointSkipEvent event = new MountPointSkipEvent();
        event.setMessage("挂载点 " + mountPointName + " 本身及其挂载点下的文件跳过复制");
        event.setTransferItem(FileTransferItem.builder()
                .from(from)
                .to(to)
                .isSkip(true)
                .build());
        return event;
    }

    /**
     * 创建挂载点跳过事件
     * @param mountPointName 挂载点名称
     * @param from 源路径
     * @param to 目标路径
     * @param transferItem 文件传输项
     * @return 事件实例
     */
    public static MountPointSkipEvent of(String mountPointName, String from, String to, FileTransferItem transferItem) {
        MountPointSkipEvent event = new MountPointSkipEvent();
        event.setMessage("挂载点 " + mountPointName + " 本身及其挂载点下的文件跳过复制");
        transferItem.setIsSkip(true);
        event.setTransferItem(transferItem);
        return event;
    }
}
