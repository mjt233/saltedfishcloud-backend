package com.xiaotao.saltedfishcloud.model.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 分页请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class PageableRequest {
    /**
     * 每页数据的大小
     */
    private Integer size;

    /**
     * 查询的页码，第一页的页码为0
     */
    private Integer page;
}
