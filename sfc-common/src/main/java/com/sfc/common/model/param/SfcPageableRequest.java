package com.sfc.common.model.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 分页请求。
 *
 * <p>由宿主 {@code com.xiaotao.saltedfishcloud.model.param.PageableRequest} 逐字迁移而来
 * （仅包名/类名变更），字段与 Lombok 注解保持一致。</p>
 *
 * <p><b>注意：</b>{@code getPage()} / {@code getSize()} 返回的是包装类型 {@link Integer}，
 * 调用方（如 {@code PageRequest.of(int, int)}）会对其拆箱，因此字段不可改为基本类型，
 * 参数缺省时需由调用方负责兜底。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class SfcPageableRequest {
    /**
     * 每页数据的大小
     */
    private Integer size;

    /**
     * 查询的页码，第一页的页码为0
     */
    private Integer page;
}
