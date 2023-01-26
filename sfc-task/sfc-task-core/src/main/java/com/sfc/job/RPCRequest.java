package com.sfc.job;

import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.Data;

@Data
public class RPCRequest {

    private Long requestId;

    private Long taskId;

    private String functionName;

    private String param;

    public Long generateIdIfAbsent() {
        if (requestId == null) {
            requestId = IdUtil.getId();
        }
        return requestId;
    }

    public String getResponseKey() {
        return "rpc_result:" + requestId;
    }
}
