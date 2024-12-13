package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.service.CrudService;

/**
 * 日志记录服务
 */
public interface LogRecordService extends CrudService<LogRecord> {
    /**
     * 添加一条记录
     */
    void saveRecord(LogRecord logRecord);
}
