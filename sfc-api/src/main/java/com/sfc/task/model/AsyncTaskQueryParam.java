package com.sfc.task.model;

import com.sfc.common.model.param.SfcPageableRequest;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AsyncTaskQueryParam extends SfcPageableRequest {
    /**
     * 用户id
     */
    private Long uid;

    /**
     * 要查询的状态
     * @see com.sfc.task.AsyncTaskConstants.Status
     */
    private List<Integer> status;

    /**
     * 要查询的任务类型，为空时查询全部类型
     */
    private String taskType;
}
