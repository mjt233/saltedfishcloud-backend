package com.xiaotao.saltedfishcloud.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigNode {
    /**
     * 配置节点
     */
    private String name;

    /**
     * 父节点的id
     */
    private String groupId;

    /**
     * 是否掩盖显示（显示为*，类似密码）
     */
    private boolean isMask;

    /**
     * 配置标题
     */
    private String title;

    /**
     * 配置值
     */
    private String value;

    /**
     * 被修改前的原值
     */
    private String originValue;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 描述
     */
    private String describe;

    /**
     * 是否只读
     */
    private boolean readonly;

    /**
     * 是否禁用
     */
    private boolean disabled;

    /**
     * 输入类型
     */
    private String inputType;

    /**
     * 可选项，用于为select、multi-select、ratio和checkbox类型提供可选值
     */
    private List<SelectOption> options;

    /**
     * form输入类型下的 参数类型引用
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String typeRef;

    /**
     * 子节点，用于为form表单类型提供子表单数据，子表单的值为json格式化字符串
     */
    private List<ConfigNode> nodes;

    /**
     * 图标
     */
    private String icon;

    /**
     * 当类型为template时使用模板内容作为内容编辑
     */
    private String template;

    /**
     * 是否在菜单中隐藏
     */
    private boolean hide;

    /**
     * 是否必填
     */
    private boolean required;
}
