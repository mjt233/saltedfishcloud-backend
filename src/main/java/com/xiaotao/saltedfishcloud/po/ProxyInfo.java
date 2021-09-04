package com.xiaotao.saltedfishcloud.po;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class ProxyInfo {
    @NotEmpty
    private String name;
    @NotNull
    private Type type;
    @NotEmpty
    private String address;
    @Max(65535)
    @Min(1)
    private int port;

    public enum Type {
        SOCKS, HTTP
    }
}
