package com.sfc.cloud.data.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@link SfcTransactionManagerProvider} 的静态持有者。
 *
 * <p>{@link com.sfc.cloud.data.utils.SfcDBUtils#executeWithTransactional} 是静态方法，
 * 无法依赖注入，故由各端在启动时把实现注册进来。</p>
 *
 * <p><b>失效策略（fail-closed）</b>：未注册实现时 {@link #getTransactionManager()} 抛出
 * {@link IllegalStateException}，而不是返回 {@code null} 或退化为无事务执行——
 * 后者会让数据一致性问题静默发生，比启动期就报错更难排查。</p>
 */
@Slf4j
public final class SfcTransactionManagerHolder {

    /** 当前生效的事务管理器提供者；由各端启动时注册 */
    private static volatile SfcTransactionManagerProvider provider;

    private SfcTransactionManagerHolder() {
    }

    /**
     * 注册事务管理器提供者。
     *
     * @param transactionManagerProvider 事务管理器提供者实现，不能为 null
     * @throws IllegalArgumentException 当参数为 null 时抛出
     */
    public static void setProvider(SfcTransactionManagerProvider transactionManagerProvider) {
        if (transactionManagerProvider == null) {
            throw new IllegalArgumentException("SfcTransactionManagerProvider 不能为 null");
        }
        provider = transactionManagerProvider;
        log.info("[事务] 已注册 SfcTransactionManagerProvider: {}",
                transactionManagerProvider.getClass().getName());
    }

    /**
     * 注销事务管理器提供者（主要供测试使用）。
     */
    public static void clear() {
        provider = null;
    }

    /**
     * 获取当前应用的事务管理器。
     *
     * @return 事务管理器
     * @throws IllegalStateException 当尚未注册提供者时抛出（fail-closed）
     */
    public static PlatformTransactionManager getTransactionManager() {
        SfcTransactionManagerProvider current = provider;
        if (current == null) {
            throw new IllegalStateException("尚未注册 SfcTransactionManagerProvider，无法执行事务方法。"
                    + "请在应用配置中声明 SfcTransactionManagerProvider Bean 并调用 "
                    + "SfcTransactionManagerHolder.setProvider(...)");
        }
        PlatformTransactionManager transactionManager = current.getTransactionManager();
        if (transactionManager == null) {
            throw new IllegalStateException("SfcTransactionManagerProvider 返回了 null 事务管理器："
                    + current.getClass().getName());
        }
        return transactionManager;
    }
}
