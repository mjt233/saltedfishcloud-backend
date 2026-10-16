package com.sfc.task.model;

import com.sfc.cloud.model.param.SfcPageableRequest;
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
}
