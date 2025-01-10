package com.xiaotao.saltedfishcloud.utils.db;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueryWrapperProperty {
    /**
     * null值条件处理策略。适用于EQ、LT、LE、GT、GE、LIKE操作。
     */
    @Builder.Default
    private final NullStrategy nullStrategy = NullStrategy.IGNORE;

    /**
     * in的条件值中包含null时，是否将该元素转换为IS NULL条件
     */
    @Builder.Default
    private final boolean inNullToIsNull = true;

    /**
     * in的条件值为null或空集合时，是否忽略in条件
     */
    @Builder.Default
    private final boolean inEmptyIgnore = true;
}
