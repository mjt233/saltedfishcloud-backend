package com.xiaotao.saltedfishcloud.service.file.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class CopyAndMoveProperty {
    /**
     * 移动目录时是否递归遍历目录来执行移动操作
     */
    @Builder.Default
    private boolean isMoveWithRecursion = true;

    /**
     * 复制目录时是否递归遍历目录来执行复制操作（暂未实现）
     */
    @Builder.Default
    private boolean isCopyWithRecursion = true;
}
