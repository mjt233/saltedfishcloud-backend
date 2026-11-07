package com.sfc.common.model;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 通用分页响应模型。
 *
 * <p>由宿主 {@code com.xiaotao.saltedfishcloud.model.CommonPageInfo} 逐字迁移而来（仅包名/类名变更），
 * 字段与序列化后的 JSON 结构与宿主完全一致，即 {@code {content, totalCount, totalPage}}，
 * 因此前端分页响应零变化。<b>字段名不可改动</b>，否则反序列化将取不到值。</p>
 *
 * <p>注意：本类需要 {@code org.springframework.data.domain.Page} 才能使用静态工厂
 * {@link #of(Page)}，该依赖在 sfc-common 中以 {@code provided} 声明，不传递给消费方。</p>
 *
 * @param <T> 分页内容元素类型
 */
@Data
@Accessors(chain = true)
public class SfcPageInfo<T> {
    /** 当前页的数据内容 */
    private List<T> content;

    /** 满足条件的总记录数（是整个查询的总数，不是当前页的记录数） */
    private long totalCount;

    /** 总页数 */
    private long totalPage;

    /**
     * 由 Spring Data 的分页对象构造分页响应。
     *
     * @param page Spring Data 分页查询结果
     * @param <T>  分页内容元素类型
     * @return 携带当前页内容、总记录数与总页数的分页响应模型
     */
    public static <T> SfcPageInfo<T> of(Page<T> page) {
        return new SfcPageInfo<T>()
                .setContent(page.getContent())
                .setTotalCount(page.getTotalElements())
                .setTotalPage(page.getTotalPages());
    }
}
