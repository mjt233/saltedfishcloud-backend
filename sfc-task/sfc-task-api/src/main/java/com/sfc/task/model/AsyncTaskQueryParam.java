package com.sfc.task.model;

import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AsyncTaskQueryParam extends PageableRequest {
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
