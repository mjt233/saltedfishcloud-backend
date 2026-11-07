package com.sfc.cloud.data.identity;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link CurrentUserProvider} 的静态持有者。
 *
 * <p><b>为什么需要静态持有：</b>{@code SfcUIDValidator} 与 {@code SfcCrudServiceImpl} 等类需要读取
 * 当前用户身份，但它们是静态工具方法、或由框架（Bean Validation）自行实例化、或本身是抽象基类，
 * 无法统一通过依赖注入拿到 provider；而仓库内 60 余处调用点依赖方法签名保持不变。
 * 因此由各端在启动时把实现注册进来。</p>
 *
 * <p><b>注册方式：</b>各端在自身的配置类中声明 {@link CurrentUserProvider} Bean，
 * 并调用 {@link #setProvider(CurrentUserProvider)}。</p>
 *
 * <p><b>失效策略（fail-closed）：</b>未注册实现时 {@link #getCurrentUid()} 返回 {@code null}、
 * {@link #currentIsAdmin()} 返回 {@code false}，语义等同"未登录且非管理员"——所有权限判定一律不通过。
 * 同时输出一条一次性告警，避免静默失效导致难以排查。</p>
 */
@Slf4j
public final class CurrentUserHolder {

    /** 当前生效的身份提供者；由各端启动时注册 */
    private static volatile CurrentUserProvider provider;

    /** 是否已就"未注册 provider"告警过，避免高频路径刷日志 */
    private static volatile boolean missingProviderWarned;

    private CurrentUserHolder() {
    }

    /**
     * 注册身份提供者。
     *
     * @param currentUserProvider 身份提供者实现，不能为 null
     * @throws IllegalArgumentException 当参数为 null 时抛出
     */
    public static void setProvider(CurrentUserProvider currentUserProvider) {
        if (currentUserProvider == null) {
            throw new IllegalArgumentException("CurrentUserProvider 不能为 null");
        }
        provider = currentUserProvider;
        // 重新注册后允许再次告警，便于热切换场景排查
        missingProviderWarned = false;
        log.info("[身份] 已注册 CurrentUserProvider: {}", currentUserProvider.getClass().getName());
    }

    /**
     * 注销身份提供者（主要供测试使用，使状态回到 fail-closed）。
     */
    public static void clear() {
        provider = null;
        missingProviderWarned = false;
    }

    /**
     * 获取当前生效的身份提供者。
     *
     * @return 身份提供者；未注册时返回 {@code null}
     */
    public static CurrentUserProvider getProvider() {
        return provider;
    }

    /**
     * 获取当前登录用户 uid（fail-closed）。
     *
     * @return 当前用户 uid；未登录或未注册 provider 时返回 {@code null}
     */
    public static Long getCurrentUid() {
        CurrentUserProvider current = provider;
        if (current == null) {
            warnMissingProvider();
            return null;
        }
        return current.getCurrentUid();
    }

    /**
     * 判断当前登录用户是否为系统管理员（fail-closed）。
     *
     * @return 是管理员返回 {@code true}；未登录或未注册 provider 时返回 {@code false}
     */
    public static boolean currentIsAdmin() {
        CurrentUserProvider current = provider;
        if (current == null) {
            warnMissingProvider();
            return false;
        }
        return current.currentIsAdmin();
    }

    /** 输出一次"未注册 provider"告警 */
    private static void warnMissingProvider() {
        if (!missingProviderWarned) {
            missingProviderWarned = true;
            log.warn("[身份] 尚未注册 CurrentUserProvider，当前用户身份一律按【未登录且非管理员】处理（fail-closed）。"
                    + "请在应用配置中声明 CurrentUserProvider Bean 并调用 CurrentUserHolder.setProvider(...)");
        }
    }
}
