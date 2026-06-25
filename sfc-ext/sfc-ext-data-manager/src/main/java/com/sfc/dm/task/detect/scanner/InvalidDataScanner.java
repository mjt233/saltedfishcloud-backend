package com.sfc.dm.task.detect.scanner;

import com.sfc.dm.model.po.InvalidDataRecord;

import java.util.function.Consumer;

/**
 * 失效数据扫描器接口
 */
public interface InvalidDataScanner {

    /**
     * 执行流式扫描，每发现一条失效数据记录时通过回调通知调用方
     *
     * @param callback 发现失效数据记录时的回调
     */
    void scan(Consumer<InvalidDataRecord> callback);
}
