package com.xiaotao.saltedfishcloud.service.permission;

import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;

/**
 * UID 访问权提供者 SPI。
 * <p>
 * 插件（如团队空间 sfc-ext-team）实现该接口并注册为 Spring Bean，
 * 声明某用户对指定 UID 资源的访问级别；核心在 {@link UidAccessResolver}
 * 中聚合所有提供者，并在文件访问鉴权（UIDValidator）中统一放行。
 */
public interface UidAccessProvider {

    /**
     * 返回指定用户对指定 uid 资源的访问级别。
     * <p>
     * 契约：与本提供者无关时返回 {@link UidAccessLevel#NONE}；
     * 实现必须幂等、只读，且不得抛出异常（异常由调用方按 NONE 处理）。
     *
     * @param user 当前登录用户（null 表示匿名）
     * @param uid  目标资源所属 UID
     * @return 访问级别，null 视为 NONE
     */
    UidAccessLevel getAccessLevel(UserPrincipal user, long uid);
}
