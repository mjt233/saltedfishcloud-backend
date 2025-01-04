package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.LogRecordQueryParam;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 日志记录管理器，负责日志信息的分发记录
 */
public interface LogRecordManager {
    /**
     * 添加一条记录
     */
    void saveRecord(LogRecord logRecord);

    /**
     * 异步添加一条记录
     */
    CompletableFuture<Void> saveRecordAsync(LogRecord logRecord);

    /**
     * 使用主日志存储器查询日志
     * @return 查询结果。若不存在主存储器，则返回null
     */
    @Nullable
    CommonPageInfo<LogRecord> queryLog(LogRecordQueryParam queryParam);

    /**
     * 注册一个日志存储器
     */
    void registerStorage(LogRecordStorage logRecordStorage);

    /**
     * 移除一个日志存储器
     * @param storageName 存储器名称
     */
    boolean removeStorage(String storageName);

    /**
     * 获取所有的日志存储器
     */
    List<LogRecordStorage> getAllStorage();
}
