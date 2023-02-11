package com.sfc.task.rpc;

import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
