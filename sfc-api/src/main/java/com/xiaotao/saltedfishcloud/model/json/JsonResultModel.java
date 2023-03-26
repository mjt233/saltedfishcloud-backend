package com.xiaotao.saltedfishcloud.model.json;

import lombok.Data;

@Data
public class JsonResultModel<T> {
    private String msg;
    private Integer code;
    private T data;
}
