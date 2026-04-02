package com.xiaotao.saltedfishcloud.model.progress.event;

import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEvent;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEventLevel;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;

/**
 * 进入目录事件
 * <p>当复制操作进入一个目录进行递归处理时触发</p>
 */
public class EnterDirEvent extends CopyProgressEvent {

    /**
     * 事件名称常量
     */
    public static final String NAME = "enter_dir";

    public EnterDirEvent() {
        super();
        setName(NAME);
        setLevel(CopyProgressEventLevel.INFO);
    }

    /**
     * 创建进入目录事件
     * @param sourcePath 源目录路径
     * @param targetPath 目标目录路径
     * @return 事件实例
     */
    public static EnterDirEvent of(String sourcePath, String targetPath) {
        EnterDirEvent event = new EnterDirEvent();
        event.setMessage("进入目录递归处理 " + sourcePath);
        event.setTransferItem(FileTransferItem.builder()
                .from(sourcePath)
                .to(targetPath)
                .build());
        return event;
    }

    /**
     * 创建进入目录事件
     * @param sourcePath 源目录路径
     * @param targetPath 目标目录路径
     * @param transferItem 文件传输项（包含目录信息）
     * @return 事件实例
     */
    public static EnterDirEvent of(String sourcePath, String targetPath, FileTransferItem transferItem) {
        EnterDirEvent event = new EnterDirEvent();
        event.setMessage("进入目录递归处理 " + targetPath);
        event.setTransferItem(transferItem);
        return event;
    }
}
