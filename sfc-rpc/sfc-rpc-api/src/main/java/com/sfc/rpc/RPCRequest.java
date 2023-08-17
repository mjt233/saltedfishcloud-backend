package com.sfc.rpc;

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

    /**
     * 本次RPC调用的请求id
     */
    private Long requestId;

    /**
     * 操作数据的id（原本只用于异步任务操作，现统一用作只需要一个id进行操作的RPC参数）
     */
    private Long taskId;

    /**
     * 调用的函数名称
     */
    private String functionName;

    /**
     * 忽略处理时，是否需要报告响应被忽略
     */
    private Boolean isReportIgnore;

    /**
     * 调用参数，一般为json字符串
     */
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
