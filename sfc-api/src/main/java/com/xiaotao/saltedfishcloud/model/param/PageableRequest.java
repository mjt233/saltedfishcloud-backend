package com.xiaotao.saltedfishcloud.model.param;

import lombok.Data;

/**
 * 分页请求
 */
@Data
public class PageableRequest {
    private Integer size;
    private Integer page;
}
