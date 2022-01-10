package com.xiaotao.saltedfishcloud.entity;

public interface JsonResult {
    int getCode();

    Object getData();

    String getMsg();

    String getJsonStr();

    static JsonResult emptySuccess() {
        return EmptySuccessJsonResult.INST;
    }
}
