package com.xiaotao.saltedfishcloud.config.security.oidc;

import com.xiaotao.saltedfishcloud.constant.StandardScopes;
import com.xiaotao.saltedfishcloud.ext.oidc.OidcScopeInfo;
import com.xiaotao.saltedfishcloud.ext.oidc.OidcScopeModule;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统内置 OIDC scope 模块。
 * <p>
 * 提供系统核心的三个标准 scope：
 * <ul>
 *   <li>{@code profile} — 个人信息读取</li>
 *   <li>{@code storage_read} — 私人网盘数据读取</li>
 *   <li>{@code storage_write} — 私人网盘数据修改</li>
 * </ul>
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BuiltinOidcScopeModule implements OidcScopeModule {

    @Override
    public String getModuleId() {
        return "system";
    }

    @Override
    public String getModuleName() {
        return "系统核心";
    }

    @Override
    public String getDescription() {
        return "咸鱼云网盘系统内置 OIDC 业务模块";
    }

    @Override
    public String getIcon() {
        return "mdi-cog";
    }

    @Override
    public List<OidcScopeInfo> getScopes() {
        return List.of(
                new OidcScopeInfo(StandardScopes.OPENID, "OIDC", "读取您的唯一标识", "mdi-identifier"),
                new OidcScopeInfo(StandardScopes.PROFILE, "个人信息", "读取您的用户名、邮箱与头像", "mdi-account-circle"),
                new OidcScopeInfo(StandardScopes.STORAGE_READ, "网盘数据读取", "读取您的私人网盘文件与文件列表", "mdi-database"),
                new OidcScopeInfo(StandardScopes.STORAGE_WRITE, "网盘数据修改", "对您的私人网盘文件进行写入、删除、重命名、移动操作", "mdi-database", true)
        );
    }
}
