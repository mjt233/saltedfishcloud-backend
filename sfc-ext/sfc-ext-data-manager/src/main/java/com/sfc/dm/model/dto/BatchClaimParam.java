package com.sfc.dm.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量认领请求参数。
 * <p>组合了失效数据筛选条件与认领目标信息。</p>
 */
@Getter
@Setter
public class BatchClaimParam {
    /**
     * 失效数据筛选条件
     */
    @Valid
    @NotNull
    private InvalidDataQuery query;

    /**
     * 认领到的目标用户网盘ID（0=公共网盘，>0=对应用户id的私人网盘）
     */
    @NotNull
    private Long targetUid;

    /**
     * 文件认领到的目录路径（不含文件名）
     */
    @NotBlank
    private String savePath;

    /**
     * 保存路径 Groovy 处理脚本（选填）。
     * <p>脚本内置变量与 {@link com.sfc.dm.service.GroovyRecordFilter} 一致：
     * {@code record}、{@code typeCheckResult}、{@code context}，
     * 且已默认导入 {@code com.xiaotao.saltedfishcloud.utils} 包下所有工具类。</p>
     * <p>返回值应为 Map，格式：{@code [path: '保存目录', name: '文件名']}。
     * 若 path 为空字符串或 null，则取参数 savePath；
     * 若 name 为空字符串或 null，则取失效数据本身的文件名 + 识别的扩展名。</p>
     */
    private String script;
}
