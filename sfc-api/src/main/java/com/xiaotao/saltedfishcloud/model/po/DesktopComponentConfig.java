package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.constant.ComponentType;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity
@Getter
@Setter
@Table(indexes = {
        @Index(name = "idx_uid", columnList = "uid"),
        @Index(name = "idx_name", columnList = "name"),
})
public class DesktopComponentConfig extends AuditModel {
    /**
     * 组件标题
     */
    private String title;

    /**
     * 组件名称
     */
    private String name;

    /**
     * 组件参数json
     */
    @Lob
    private String params;

    /**
     * 备注
     */
    private String remark;

    /**
     * 组件类型
     * @see ComponentType
     */
    private String type;

    /**
     * 显示顺序
     */
    private Integer showOrder;

    /**
     * 布局占用的单位宽度
     */
    private Integer width;

    /**
     * 布局占用的单位高度
     */
    private Integer height;

    /**
     * 是否使用卡片样式
     */
    private Integer useCard;

    /**
     * 是否启用
     */
    private Integer enabled;
}
