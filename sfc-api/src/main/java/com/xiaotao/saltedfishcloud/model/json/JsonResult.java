package com.xiaotao.saltedfishcloud.model.json;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface JsonResult<T> {
    int getCode();

    T getData();

    String getMsg();

    @JsonIgnore
    String getJsonStr();

    static JsonResult<Object> emptySuccess() {
        return EmptySuccessJsonResult.INST;
    }
}
