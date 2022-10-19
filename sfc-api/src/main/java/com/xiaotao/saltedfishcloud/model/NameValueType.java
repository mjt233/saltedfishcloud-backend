package com.xiaotao.saltedfishcloud.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NameValueType<T> {
    private String name;
    private T value;
}
