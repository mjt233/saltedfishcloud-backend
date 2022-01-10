package com.xiaotao.saltedfishcloud.entity;

public interface JsonResult {
    int getCode();

    Object getData();

    String getMsg();

    default JsonResult emptySuccess() {
        return EmptySuccessJsonResult.INST;
    }
}
