package com.xiaotao.saltedfishcloud.model.progress.event;

import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEvent;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEventLevel;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;

/**
 * 更新文件记录完成事件
 * <p>当文件记录信息更新完成时触发</p>
 */
public class UpdateFileRecordCompleteEvent extends CopyProgressEvent {

    /**
     * 事件名称常量
     */
    public static final String NAME = "update_file_record_complete";

    public UpdateFileRecordCompleteEvent() {
        super();
        setName(NAME);
        setLevel(CopyProgressEventLevel.INFO);
        setMessage("更新文件记录信息完成");
    }

    /**
     * 创建更新文件记录完成事件
     * @return 事件实例
     */
    public static UpdateFileRecordCompleteEvent of() {
        return new UpdateFileRecordCompleteEvent();
    }

    /**
     * 创建更新文件记录完成事件
     * @param transferItem 文件传输项
     * @return 事件实例
     */
    public static UpdateFileRecordCompleteEvent of(FileTransferItem transferItem) {
        UpdateFileRecordCompleteEvent event = new UpdateFileRecordCompleteEvent();
        event.setTransferItem(transferItem);
        return event;
    }
}
