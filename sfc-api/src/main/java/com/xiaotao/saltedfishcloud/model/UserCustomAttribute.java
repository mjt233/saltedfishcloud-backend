package com.xiaotao.saltedfishcloud.model;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;

import javax.persistence.Entity;

/**
 * 用户自定义属性。用于将敏感数据封装为一个关联数据，以便在前端通过组件进行网络请求和传输而不暴露关键参数。
 */
@Entity
public class UserCustomAttribute extends AuditModel {
    /**
     * 标签
     */
    private String label;

    /**
     * 备注
     */
    private String remark;

    /**
     * 主要内容（json）
     */
    private String json;
}
