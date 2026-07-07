package com.xiaotao.saltedfishcloud.ext.oidc;

import lombok.Data;

/**
 * OIDC scope 元数据。
 * <p>
 * 描述一个 OAuth2 scope 的标识、显示名称、用途说明及图标，
 * 挂靠在 {@link OidcScopeModule 模块} 下统一管理。
 * </p>
 */
@Data
public class OidcScopeInfo {

    /** scope 唯一标识，例如 "storage_read"、"profile" */
    private String id;

    /** scope 显示名称，例如 "私人网盘数据读取" */
    private String name;

    /** scope 描述，说明该 scope 授予的权限范围 */
    private String description;

    /** scope 图标 URL 或 CSS 类名 */
    private String icon;

    /** 是否为危险权限 */
    private Boolean isDanger;

    public OidcScopeInfo(String id, String name, String description, String icon, Boolean isDanger) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.isDanger = isDanger;
    }

    public OidcScopeInfo(String id, String name, String description, String icon) {
        this(id, name, description, icon, false);
    }

    public OidcScopeInfo(String id, String name, String description) {
        this(id, name, description, "mdi-key", false);
    }
}
