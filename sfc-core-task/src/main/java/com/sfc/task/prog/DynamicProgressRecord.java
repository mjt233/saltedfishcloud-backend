package com.sfc.task.prog;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 带扩展字段的任务进度记录。
 *
 * <p>用于把执行者上报的完整进度快照写入任务进度缓存：{@link ProgressRecord} 的固定字段
 * （{@code loaded} / {@code total} / {@code speed} / {@code lastUpdateTime}）之外的字段保存在
 * {@code extensions} 中。序列化时扩展字段展开为与固定字段同级的 JSON 属性，使
 * {@code /api/asyncTask/getProg} 的响应形态保持与只有固定字段时一致。</p>
 */
public class DynamicProgressRecord extends ProgressRecord {

    /**
     * 进度扩展字段：键为字段名，值为字段取值
     */
    private final Map<String, Object> extensions = new LinkedHashMap<>();

    /**
     * 创建一个空的进度记录（供 JSON 反序列化使用）。
     */
    @SuppressWarnings("unused")
    public DynamicProgressRecord() {
    }

    /**
     * 创建一个带扩展字段的进度记录。
     *
     * @param extensions 扩展字段，可为 null
     */
    public DynamicProgressRecord(Map<String, Object> extensions) {
        if (extensions != null) {
            this.extensions.putAll(extensions);
        }
    }

    /**
     * 读取进度扩展字段。
     *
     * <p>被 Jackson 用作 any-getter：序列化时扩展字段展开为与固定字段同级的 JSON 属性，
     * 不会额外产生名为 {@code extensions} 的属性。</p>
     *
     * @return 扩展字段映射
     */
    @JsonAnyGetter
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    /**
     * 反序列化时接收一个扩展字段。
     *
     * @param name  字段名
     * @param value 字段取值
     */
    @JsonAnySetter
    public void putExtension(String name, Object value) {
        if (name != null) {
            extensions.put(name, value);
        }
    }
}
