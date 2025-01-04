package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.LogRecordQueryParam;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;

/**
 * 日志记录存储器
 */
public interface LogRecordStorage {
    /**
     * 保存一个日志记录
     */
    void saveRecord(LogRecord logRecord);

    /**
     * 查询日志
     */
    CommonPageInfo<LogRecord> query(LogRecordQueryParam queryParam);

    /**
     * 获取存储器名称
     */
    String getName();

    /**
     * 是否已激活该存储
     */
    boolean isActive();

    /**
     * 激活存储
     */
    void active();

    /**
     * 停止存储
     */
    void stop();

}
