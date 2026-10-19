package com.sfc.task;

import com.sfc.task.model.AsyncTaskQueryParam;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.common.model.SfcPageInfo;

/**
 * 异步任务数据库操作服务类
 */
public interface AsyncTaskRecordService {
    /**
     * 支持分页的按创建人、状态查询任务记录
     * @param param     查询参数
     * @return          查询结果
     */
    SfcPageInfo<AsyncTaskRecord> listRecord(AsyncTaskQueryParam param);
}
