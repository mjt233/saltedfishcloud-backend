package com.xiaotao.saltedfishcloud.model.progress.event;

import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEvent;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEventLevel;

/**
 * 更新文件记录开始事件
 * <p>当开始更新文件记录信息时触发</p>
 */
public class UpdateFileRecordStartEvent extends CopyProgressEvent {

    /**
     * 事件名称常量
     */
    public static final String NAME = "update_file_record_start";

    public UpdateFileRecordStartEvent() {
        super();
        setName(NAME);
        setLevel(CopyProgressEventLevel.INFO);
        setMessage("更新文件记录信息开始执行");
    }

    /**
     * 创建更新文件记录开始事件
     * @return 事件实例
     */
    public static UpdateFileRecordStartEvent of() {
        return new UpdateFileRecordStartEvent();
    }
}
