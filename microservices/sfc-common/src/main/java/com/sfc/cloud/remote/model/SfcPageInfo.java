package com.sfc.cloud.remote.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * sfc-core 分页结果契约。
 *
 * <p>字段与 core 的 {@code com.xiaotao.saltedfishcloud.model.CommonPageInfo} 逐字对齐，
 * 用于跨服务反序列化分页接口（如文件搜索）的响应体。字段名不可随意改动，否则反序列化将取不到值。</p>
 *
 * @param <T> 分页内容元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SfcPageInfo<T> {

    /** 当前页的数据内容 */
    private List<T> content;

    /** 满足条件的总记录数 */
    private long totalCount;

    /** 总页数 */
    private long totalPage;
}
