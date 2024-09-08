package com.xiaotao.saltedfishcloud.model.json;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface JsonResult<T> {
    /**
     * 响应代码
     */
    int getCode();

    /**
     * 业务代码
     */
    int getBusinessCode();

    /**
     * 主要数据
     */
    T getData();

    /**
     * 消息
     */
    String getMsg();

    @JsonIgnore
    String getJsonStr();

    static JsonResult<Object> emptySuccess() {
        return EmptySuccessJsonResult.INST;
    }
}
