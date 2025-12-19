package com.xiaotao.saltedfishcloud.model.po;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.InetSocketAddress;
import java.net.Proxy;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity(name = "Proxy")
@Table(name = "proxy", indexes = {
        @Index(name = "i_uid", columnList = "uid,name")
})
public class ProxyInfo extends AuditModel {
    @NotEmpty
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    private Type type;

    @NotEmpty
    private String address;

    @Max(65535)
    @Min(1)
    private Integer port;

    /**
     * 测试代理是否正常工作用的url
     */
    private String testUrl;

    public enum Type {
        SOCKS, HTTP
    }

    public Proxy toProxy() {
        return new Proxy(type == Type.SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP, new InetSocketAddress(address, port));
    }

    @Override
    public String toString() {
        return name + " - " + type + "://" + address + ":" + port;
    }
}
