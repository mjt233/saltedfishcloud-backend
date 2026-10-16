package com.xiaotao.saltedfishcloud.config.shared;

import com.sfc.cloud.data.identity.CurrentUserHolder;
import com.sfc.cloud.data.identity.CurrentUserProvider;
import com.sfc.cloud.data.tx.SfcTransactionManagerHolder;
import com.sfc.cloud.data.tx.SfcTransactionManagerProvider;
import com.sfc.cloud.utils.SfcMapperHolder;
import com.sfc.cloud.utils.SfcTypeUtils;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 共享基座 SPI 的宿主侧装配。
 *
 * <p>Phase 1 把数据访问基座迁到了 {@code sfc-common-data}，其中的
 * {@code SfcUIDValidator} / {@code SfcCrudServiceImpl} / {@code SfcDBUtils} 是<b>静态</b>工具，
 * 无法通过依赖注入获得"当前用户"与"事务管理器"，因此共享基座把它们抽象成 SPI，
 * 由各端在启动时注册到静态持有者里。本类负责宿主侧的注册。</p>
 *
 * <p><b>为什么用 {@link InitializingBean} 而不是 {@code @PostConstruct}：</b>
 * 需要把上面两个 provider Bean 注册进持有者；以 Bean 形式注册可避免在同一个
 * {@code @Configuration} 内构造器自注入 provider 造成的循环引用，
 * 同时晚于 provider 自身初始化，顺序明确。</p>
 *
 * <p><b>fail-closed 语义</b>：若本配置未生效，共享基座的权限判定一律不通过、
 * 事务方法直接抛异常——这是刻意设计，避免"静默放行 / 静默无事务"。</p>
 */
@Configuration
public class SfcSharedBaseBridgeConfiguration {

    /**
     * 当前用户身份 provider（宿主实现：委托 Spring Security 上下文）。
     *
     * @return 身份 provider
     */
    @Bean
    public CurrentUserProvider hostCurrentUserProvider() {
        return new HostCurrentUserProvider();
    }

    /**
     * 事务管理器 provider（宿主实现：直接使用容器中的 {@link PlatformTransactionManager}）。
     *
     * @param transactionManager 容器中的事务管理器
     * @return 事务管理器 provider
     */
    @Bean
    public SfcTransactionManagerProvider hostTransactionManagerProvider(PlatformTransactionManager transactionManager) {
        return () -> transactionManager;
    }

    /**
     * 把两个 provider 注册进共享基座的静态持有者。
     *
     * @param currentUserProvider        身份 provider
     * @param transactionManagerProvider 事务管理器 provider
     * @return 完成注册的初始化回调
     */
    @Bean
    public InitializingBean sharedBaseHolderBinder(CurrentUserProvider currentUserProvider,
                                                   SfcTransactionManagerProvider transactionManagerProvider) {
        return () -> {
            CurrentUserHolder.setProvider(currentUserProvider);
            SfcTransactionManagerHolder.setProvider(transactionManagerProvider);
        };
    }

    /**
     * 注册「字符串 → 配置类对象」的扩展转换器。
     *
     * <p>宿主原 {@code TypeUtils.convert} 里有一段依赖宿主注解 {@code @ConfigPropertyEntity} 的分支：
     * 当目标类型标注了该注解、而配置库里的值是一个 JSON 字符串时，用 Jackson 反序列化成配置对象。
     * 这段逻辑是<b>配置绑定的必需路径</b>（{@code ConfigService.bindPropertyEntity}
     * → {@code ObjectUtils.copyMapToBean} → {@code SfcTypeUtils.convert}），
     * 例如 {@code SysCommonConfig} 的邮件配置字段就依赖它；缺失会直接导致启动失败：
     * {@code 无法将 class java.lang.String 转为 ...MailProperties}。</p>
     *
     * <p>迁移到共享模块后该注解不可见，因此改为由宿主把实现注册进
     * {@link SfcTypeUtils#setStringObjectConverter}。</p>
     *
     * <p><b>为什么必须是 {@code static} 的 {@link BeanFactoryPostProcessor}：</b>
     * 该转换器会在<b>普通 Bean 实例化过程中</b>被调用（{@code sysCommonConfig} 这个 @Bean
     * 一创建就要绑定配置），而普通 {@code @Bean} 之间的创建顺序没有保证；
     * {@code BeanFactoryPostProcessor} 在所有常规 Bean 实例化之前执行，是唯一可靠的注册时机。
     * 方法声明为 {@code static} 则是为了避免配置类自身被提前实例化（Spring 官方推荐做法）。</p>
     *
     * @return 注册转换器的后置处理器
     */
    @Bean
    public static BeanFactoryPostProcessor hostStringObjectConverterRegistrar() {
        return beanFactory -> SfcTypeUtils.setStringObjectConverter((input, targetType) -> {
            if (!targetType.isAnnotationPresent(ConfigPropertyEntity.class)) {
                // 与本 SPI 的约定一致：不是本实现负责的类型，返回 null 交由内置逻辑处理
                return null;
            }
            try {
                return SfcMapperHolder.parseJson(input, targetType);
            } catch (Exception e) {
                // 与宿主原实现一致：解析失败则忽略，继续尝试其它转换方式
                return null;
            }
        });
    }
}
