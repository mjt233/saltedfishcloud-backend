package com.sfc.cloud.remote.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sfc.cloud.remote.SfcRemoteApiException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * sfc-core 响应信封。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SfcJsonResult<T> {

    /** 响应代码 */
    private int code;

    /** 细分业务码 */
    private int businessCode = 200;

    /** 提示消息 */
    private String msg;

    /** 业务数据 */
    private T data;

    /** 是否业务成功 */
    @JsonIgnore
    public boolean isSuccess() {
        return code == 200;
    }

    /** 断言业务成功 */
    public SfcJsonResult<T> requireSuccess() {
        if (!isSuccess()) {
            throw new SfcRemoteApiException(code, code, businessCode, null, msg);
        }
        return this;
    }

    /** 断言成功并取出业务数据 */
    public T getDataOrThrow() {
        return requireSuccess().getData();
    }
}
