package com.sfc.cloud.data.tx;

import org.springframework.transaction.PlatformTransactionManager;

/**
 * 事务管理器提供者 SPI。
 *
 * <p>与 {@link com.sfc.cloud.data.identity.CurrentUserProvider} 同样的动机：{@code SfcDBUtils}
 * 提供的是<b>静态</b>工具方法（{@code executeWithTransactional} 三个重载），无法通过依赖注入获得
 * {@code PlatformTransactionManager}；而各端从容器中取事务管理器的方式又互不相同。
 * 因此把"从哪里拿事务管理器"抽成 SPI，由各端在启动时注册。</p>
 *
 * <p><b>失效策略（fail-closed）</b>：未注册实现时调用 {@code executeWithTransactional} 会立即抛出
 * {@link IllegalStateException}——事务方法静默降级为"无事务执行"会掩盖数据一致性问题，比直接失败更危险。</p>
 */
public interface SfcTransactionManagerProvider {

    /**
     * 获取当前应用的事务管理器。
     *
     * @return 事务管理器；实现方不应返回 {@code null}
     */
    PlatformTransactionManager getTransactionManager();
}
