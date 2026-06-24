package com.sfc.dm.constant;

import com.xiaotao.saltedfishcloud.constant.error.ErrorInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InvalidDataError implements ErrorInfo {
    FILTER_EXPIRED(8001, 400, "筛选结果已过期"),
    GROOVY_COMPILE_FAIL(8002, 400, "脚本编译失败, 请检查语法"),
    GROOVY_TIMEOUT(8003, 400, "脚本执行超时，请优化脚本"),
    GROOVY_EXECUTE_ERROR(8004, 400, "脚本执行异常"),;
    final int code;
    final int status;
    final String message;
}
