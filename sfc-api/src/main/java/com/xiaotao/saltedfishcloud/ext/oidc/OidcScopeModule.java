package com.xiaotao.saltedfishcloud.ext.oidc;

import java.util.List;

/**
 * OIDC scope 模块贡献点接口。
 * <p>
 * 扩展模块（如 sfc-ext-*）通过实现此接口并注册为 Spring Bean，
 * 向系统注册一组 scope。注册后的 scope 将自动出现在：
 * <ul>
 *   <li>OIDC 发现文档 {@code /.well-known/openid-configuration} 的 {@code scopes_supported} 字段</li>
 *   <li>所有 OAuth2 客户端的可用 scope 列表</li>
 *   <li>授权同意页面</li>
 * </ul>
 * </p>
 * <p>
 * 系统内置模块（moduleId = {@code "system"}）已注册 {@code profile}、{@code storage_read}、{@code storage_write}。
 * </p>
 *
 * @see OidcScopeInfo
 */
public interface OidcScopeModule {

    /** 模块唯一标识，例如 "system"、"webdav" */
    String getModuleId();

    /** 模块显示名称，例如 "系统核心"、"WebDAV 服务" */
    String getModuleName();

    /** 模块描述 */
    String getDescription();

    /**
     * 模块图标 URL 或 MaterialDesignIcon名 或 base64。
     * <p>默认返回 {@code null}，子类可重写以提供图标。</p>
     *
     * @return 图标标识
     */
    String getIcon();

    /** 该模块提供的所有 scope */
    List<OidcScopeInfo> getScopes();
}
