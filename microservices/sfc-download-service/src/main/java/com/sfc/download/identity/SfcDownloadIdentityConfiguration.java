package com.sfc.download.identity;

import com.sfc.cloud.data.identity.CurrentUserHolder;
import com.sfc.cloud.data.identity.CurrentUserProvider;
import com.sfc.cloud.remote.client.SfcCoreUserClient;
import com.sfc.cloud.trust.SfcTrustHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 离线下载微服务的身份装配。
 *
 * <p><b>装配方式：组件扫描。</b>本类位于 {@code com.sfc.download.identity}，落在启动类
 * {@link com.sfc.download.DownloadServiceApplication} 的 {@code @SpringBootApplication} 扫描范围
 * （{@code com.sfc.download.**}）之内，因此无需额外的 {@code @Import} 清单。</p>
 *
 * <p><b>做了三件事：</b></p>
 * <ol>
 *   <li>声明 {@link CurrentUserProvider} 的微服务实现（委托 {@link SfcCurrentUserSupport}）；</li>
 *   <li>在启动期把该实现注册进静态持有者 {@link CurrentUserHolder#setProvider(CurrentUserProvider)}，
 *       使 {@code SfcUIDValidator} 等静态工具能取得当前身份；</li>
 *   <li>把远程用户客户端（可选依赖）注册给 {@link SfcCurrentUserSupport}，用于管理员判定回源。</li>
 * </ol>
 *
 * <p><b>为什么用 {@link InitializingBean} 而不是 {@code @PostConstruct}：</b>与宿主侧
 * {@code SfcSharedBaseBridgeConfiguration} 保持同一手法——以 Bean 形式注册可避免在同一个
 * {@code @Configuration} 内构造器自注入造成的循环引用，同时晚于被注册对象自身初始化；
 * 且 {@code InitializingBean} Bean 在容器 refresh 的单例实例化阶段完成回调，
 * 早于 Web 容器开始接收请求，因此第一个请求到达时身份必然已就绪。</p>
 *
 * <p><b>fail-closed 语义：</b>若本配置未生效，共享基座的权限判定一律不通过（uid 为 null、
 * admin 为 false），本服务的 uid 校验会全部拒绝——这是刻意设计，避免「静默放行」。</p>
 *
 * @see SfcCurrentUserSupport
 * @see SfcCurrentUserProvider
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class SfcDownloadIdentityConfiguration {

    /**
     * 微服务侧的身份提供者（{@link CurrentUserProvider} SPI 实现）。
     *
     * @return 身份提供者
     */
    @Bean
    public CurrentUserProvider sfcCurrentUserProvider() {
        return new SfcCurrentUserProvider();
    }

    /**
     * 把身份相关实现注册进共享基座与身份支持的静态持有者。
     *
     * <p>远程用户客户端以 {@link ObjectProvider} 形式传入（可选依赖）：{@code sfc-remote-client}
     * 的自动装配提供它，取不到时管理员判定自动降级为 {@code false}，不影响本服务启动。</p>
     *
     * @param currentUserProvider 身份提供者
     * @param userClientProvider  远程用户客户端的可选提供者
     * @return 完成注册的初始化回调
     */
    @Bean
    public InitializingBean sfcIdentityHolderBinder(CurrentUserProvider currentUserProvider,
                                                    ObjectProvider<SfcCoreUserClient> userClientProvider) {
        return () -> {
            SfcCurrentUserSupport.setUserClientProvider(userClientProvider);
            CurrentUserHolder.setProvider(currentUserProvider);
            log.info("[身份] sfc-download-service 身份装配完成：uid 取自网关注入的 {} 信任头",
                    SfcTrustHeaders.UID);
        };
    }
}
