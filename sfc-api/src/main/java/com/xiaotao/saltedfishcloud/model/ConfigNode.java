package com.xiaotao.saltedfishcloud.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaotao.saltedfishcloud.constant.ConfigInputType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Accessors(chain = true)
public class ConfigNode {

    /**
     * 快捷创建一个name-value节点
     * @param nameAndTitle  id名称与标题
     * @param value         值
     */
    public ConfigNode(String nameAndTitle, Object value) {
        this.name = nameAndTitle;
        this.title = nameAndTitle;
        this.value = value;
    }

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
    private Object value;

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
     * 排序
     */
    private long sort = 0;

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
     *
     * @see ConfigInputType
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

    /**
     * 额外参数，当type为 {@link ConfigInputType#TEMPLATE} 会传递给模板
     */
    private Map<String, Object> params;

    /**
     * 是否独占一行
     */
    private Boolean isRow;

    public ConfigNode useTemplate(String template) {
        this.setInputType("template");
        this.setTemplate(template);
        return this;
    }
}
