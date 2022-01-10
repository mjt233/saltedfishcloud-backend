package com.xiaotao.saltedfishcloud.entity;

public enum EmptySuccessJsonResult implements JsonResult {
    INST;
    @Override
    public int getCode() {
        return 200;
    }

    @Override
    public Object getData() {
        return null;
    }

    @Override
    public String getMsg() {
        return "ok";
    }

    @Override
    public String getJsonStr() {
        return "{\"code\":200,\"data\":null,\"msg\":\"ok\"}";
    }


}
