package com.sfc.task;

import com.sfc.task.model.AsyncTaskQueryParam;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;

/**
 * 异步任务数据库操作服务类
 */
public interface AsyncTaskRecordService {
    /**
     * 支持分页的按创建人、状态查询任务记录
     * @param param     查询参数
     * @return          查询结果
     */
    CommonPageInfo<AsyncTaskRecord> listRecord(AsyncTaskQueryParam param);
}
