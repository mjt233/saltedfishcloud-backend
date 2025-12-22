package com.sfc.nwt.upnp.model.xml.service.av;

import jakarta.xml.bind.annotation.*;
import lombok.Data;

import java.util.List;

/**
 * SCPD (Service Control Protocol Definition) 实体类
 * 用于反序列化UPnP服务描述XML
 * 对应SCPD XML文档的根元素
 */
@Data
@XmlRootElement(name = "scpd", namespace = "urn:schemas-upnp-org:service-1-0")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(namespace = "urn:schemas-upnp-org:service-1-0")
public class Scpd {

    /**
     * 协议版本信息
     */
    @XmlElement(name = "specVersion")
    private SpecVersion specVersion;

    /**
     * 动作列表，包含该服务支持的所有操作
     */
    @XmlElementWrapper(name = "actionList")
    @XmlElement(name = "action")
    private List<Action> actionList;

    /**
     * 服务状态表，包含该服务的所有状态变量定义
     */
    @XmlElementWrapper(name = "serviceStateTable")
    @XmlElement(name = "stateVariable")
    private List<StateVariable> serviceStateTable;

    /**
     * 协议版本信息类
     * 包含UPnP服务协议的版本号
     */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SpecVersion {
        /**
         * 主版本号
         */
        @XmlElement
        private Integer major;

        /**
         * 次版本号
         */
        @XmlElement
        private Integer minor;
    }

    /**
     * 动作类
     * 定义一个UPnP服务支持的操作
     */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Action {
        /**
         * 动作名称
         */
        @XmlElement
        private String name;

        /**
         * 参数列表，包含该动作的所有输入输出参数
         */
        @XmlElementWrapper(name = "argumentList")
        @XmlElement(name = "argument")
        private List<Argument> argumentList;
    }

    /**
     * 参数类
     * 定义动作的输入或输出参数
     */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Argument {
        /**
         * 参数名称
         */
        @XmlElement
        private String name;

        /**
         * 参数方向：in(输入) 或 out(输出)
         */
        @XmlElement
        private String direction;

        /**
         * 相关的状态变量名称
         */
        @XmlElement
        private String relatedStateVariable;
    }

    /**
     * 状态变量类
     * 定义服务的状态变量
     */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class StateVariable {
        /**
         * 事件发送标志：yes(发送事件) 或 no(不发送事件)
         */
        @XmlAttribute
        private String sendEvents;

        /**
         * 状态变量名称
         */
        @XmlElement
        private String name;

        /**
         * 数据类型
         * 如：string, ui4, i4 等
         */
        @XmlElement
        private String dataType;

        /**
         * 允许值列表
         * 定义该状态变量允许的枚举值
         */
        @XmlElementWrapper(name = "allowedValueList")
        @XmlElement(name = "allowedValue")
        private List<String> allowedValueList;

        /**
         * 允许值范围
         * 定义数值类型状态变量的取值范围
         */
        @XmlElement(name = "allowedValueRange")
        private AllowedValueRange allowedValueRange;

        /**
         * 默认值
         */
        @XmlElement
        private String defaultValue;
    }

    /**
     * 允许值范围类
     * 定义数值类型状态变量的取值范围
     */
    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class AllowedValueRange {
        /**
         * 最小值
         */
        @XmlElement
        private Integer minimum;

        /**
         * 最大值
         */
        @XmlElement
        private Integer maximum;

        /**
         * 步长
         */
        @XmlElement
        private Integer step;
    }
}