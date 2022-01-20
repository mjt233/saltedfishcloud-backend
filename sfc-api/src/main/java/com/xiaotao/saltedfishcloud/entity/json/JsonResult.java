package com.xiaotao.saltedfishcloud.entity.json;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface JsonResult {
    int getCode();

    Object getData();

    String getMsg();

    @JsonIgnore
    String getJsonStr();

    static JsonResult emptySuccess() {
        return EmptySuccessJsonResult.INST;
    }
}
