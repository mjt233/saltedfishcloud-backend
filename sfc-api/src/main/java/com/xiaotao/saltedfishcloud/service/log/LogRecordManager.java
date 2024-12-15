package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.model.po.LogRecord;

/**
 * 日志记录管理器，负责日志信息的分发记录
 */
public interface LogRecordManager {
    /**
     * 添加一条记录
     */
    void saveRecord(LogRecord logRecord);

}
