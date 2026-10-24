package com.xiaotao.saltedfishcloud.task.worker;

import com.sfc.cloud.task.worker.SfcTaskHandler;
import com.sfc.cloud.task.worker.SfcTaskWorkerProperties;
import com.sfc.cloud.task.worker.SfcTaskWorkerRegistry;
import com.sfc.task.AsyncTaskFactory;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * sfc-core 内嵌任务 Worker 的桥接配置。
 *
 * <p>把容器中的全部 {@link AsyncTaskFactory} 适配为 Worker SDK 的任务处理器 {@link SfcTaskHandler}，
 * 并以这些处理器构造 {@link SfcTaskWorkerRegistry}。SDK 自动装配中的同名 Bean 标注了
 * {@code @ConditionalOnMissingBean}，因此本配置定义的注册表会覆盖 SDK 的默认空注册表，
 * 令 core 节点以 {@code CORE} 角色参与任务认领与执行。</p>
 *
 * <p>本配置的启用条件与 SDK 自动装配保持一致：仅在 Servlet Web 应用、且
 * {@code sfc.task.worker.enabled} 未显式关闭（默认开启）时生效。</p>
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "sfc.task.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AsyncTaskWorkerConfiguration {

    /**
     * 构造覆盖 SDK 默认空注册表的任务处理器注册表。
     *
     * <p>直接通过 {@link ObjectProvider} 取容器中的 {@link AsyncTaskFactory} Bean（按 {@code @Order}
     * 排序），不依赖任务工厂注册表的初始化顺序。全部任务工厂都会生成一个桥接处理器，
     * 由注册表统一校验任务类型非空且不重复。</p>
     *
     * @param factoryProvider 容器中的全部异步任务工厂
     * @param properties      Worker 配置项，由 SDK 自动装配绑定（前缀 {@code sfc.task.worker}）
     * @param userService     用户服务，供任务执行前绑定任务创建者的安全上下文
     * @return 以全部任务工厂为能力声明的任务处理器注册表
     */
    @Bean
    public SfcTaskWorkerRegistry sfcTaskWorkerRegistry(ObjectProvider<AsyncTaskFactory> factoryProvider,
                                                       SfcTaskWorkerProperties properties,
                                                       UserService userService) {
        List<SfcTaskHandler> handlers = factoryProvider.orderedStream()
                .<SfcTaskHandler>map(factory -> new AsyncTaskFactoryHandler(factory, userService))
                .toList();
        return new SfcTaskWorkerRegistry(handlers, properties);
    }
}
