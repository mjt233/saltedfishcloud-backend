package com.sfc.download.engine;

import com.sfc.common.model.ConfigNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 下载类型的能力声明。
 *
 * <p>由 {@link DownloadEngineRegistry#capabilities()} 汇总所有已注册引擎生成，经
 * {@code GET /api/task/download/capabilities} 提供给前端：前端据此渲染可选类型，
 * 并根据 {@link #available} 与 {@link #reason} 对当前不可用的类型做禁用与提示。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadCapability {

    /**
     * 下载类型。
     */
    private DownloadType type;

    /**
     * 面向用户展示的类型名称。
     */
    private String name;

    /**
     * 承载该类型的下载引擎唯一标识（取值见 {@link DownloadEngine#getEngineId()}，例如 {@code http}、
     * {@code git}、{@code aria2}、{@code ed2k}）。
     *
     * <p>供前端做诊断与埋点，<b>不参与类型判断</b>：同一引擎支持多种类型时（aria2 同时承载磁力链与种子），
     * 这些类型的本字段相同。</p>
     */
    private String engineId;

    /**
     * 当前是否可用：{@code false} 表示本实例暂时无法执行该类型（例如 aria2 未启用、引擎未接入）。
     */
    private boolean available;

    /**
     * 不可用原因；{@code available} 为 {@code true} 时为 {@code null}。
     */
    private String reason;

    /**
     * 该类型支持的选项元数据，用于前端动态渲染创建表单；无专属选项时为空列表。
     *
     * <p>每个元素是一个 {@link ConfigNode}：{@code name} 是创建任务请求体 {@code options}
     * 中的键名，{@code inputType} 决定输入控件，{@code defaultValue} 为默认值，
     * {@code describe} 为说明文案，{@code mask} 表示凭证类选项需打码，{@code sort} 决定展示顺序。</p>
     */
    private List<ConfigNode> options;
}
