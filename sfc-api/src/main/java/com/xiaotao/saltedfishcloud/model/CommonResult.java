package com.xiaotao.saltedfishcloud.model;

import com.xiaotao.saltedfishcloud.model.json.AbstractJsonResult;
import lombok.*;

/**
 * 临时兼容对象，用于解决JsonResult的反序列化问题。<br>
 * JsonResult过度设计了，需要简化。
 * @param <T>   数据类型
 */
@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CommonResult<T> extends AbstractJsonResult<T> {
    private String msg;
    private T data;
    private int code;
}
