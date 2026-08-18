package com.xiaotao.saltedfishcloud.service.permission;

import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;

/**
 * UID 访问级别解析器。
 * <p>
 * 接口定义于 sfc-api，具体实现（聚合所有 {@link UidAccessProvider} Bean）位于 sfc-core，
 * 并在应用启动时通过静态持有器注册；未注册实现时各方法按「无扩展权限」处理，
 * 保证在不安装任何提供者插件的情况下系统行为与旧版完全一致。
 */
public interface UidAccessResolver {

    /**
     * 解析指定用户对指定 uid 资源的访问级别。
     *
     * @param user 当前登录用户（null 表示匿名）
     * @param uid  目标资源所属 UID
     * @return 访问级别（正常非 null）
     */
    UidAccessLevel getAccessLevel(UserPrincipal user, long uid);

    /**
     * 获取当前全局解析器实例。
     *
     * @return 解析器实例，未注册时为 null
     */
    static UidAccessResolver getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 注册全局解析器实例（由 sfc-core 实现于启动时调用）。
     *
     * @param resolver 解析器实现
     */
    static void setInstance(UidAccessResolver resolver) {
        Holder.INSTANCE = resolver;
    }

    /**
     * 静态持有器：保存全局解析器实例
     */
    final class Holder {
        /**
         * 全局解析器实例（volatile 保证跨线程可见性）
         */
        private static volatile UidAccessResolver INSTANCE;

        private Holder() {
        }
    }
}
