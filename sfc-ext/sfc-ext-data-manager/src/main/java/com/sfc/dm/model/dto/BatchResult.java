package com.sfc.dm.model.dto;

import java.util.List;

/**
 * 批量操作结果
 *
 * @param success 成功数量
 * @param fail    失败数量
 * @param errors  错误信息列表
 */
public record BatchResult(int success, int fail, List<String> errors) {
}
