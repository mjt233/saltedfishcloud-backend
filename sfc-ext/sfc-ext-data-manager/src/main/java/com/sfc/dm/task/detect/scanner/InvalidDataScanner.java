package com.sfc.dm.task.detect.scanner;

import com.sfc.dm.model.po.InvalidDataRecord;

import java.util.List;

/**
 * 失效数据扫描器接口
 */
public interface InvalidDataScanner {

    /**
     * 执行扫描，返回发现的失效数据记录列表
     *
     * @return 失效数据记录列表
     */
    List<InvalidDataRecord> scan();
}
