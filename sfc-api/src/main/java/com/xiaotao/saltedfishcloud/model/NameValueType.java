package com.xiaotao.saltedfishcloud.model;

import lombok.Data;

@Data
public class NameValueType<T> {
    private String name;
    private T value;
}
