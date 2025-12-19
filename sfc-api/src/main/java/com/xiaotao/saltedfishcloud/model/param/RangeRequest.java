package com.xiaotao.saltedfishcloud.model.param;

/**
 * 数据范围请求
 */
public record RangeRequest<T>(T begin, T end) {
}
