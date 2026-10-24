package com.sfc.cloud.task.worker;

import com.sfc.cloud.auth.SfcInternalTokenIssuer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 任务 Worker SDK 自动装配。
 *
 * <p>仅在 Servlet Web 应用中生效（中断端点依赖 Spring MVC），并受
 * {@code sfc.task.worker.enabled}（默认 {@code true}）控制。装配内容：</p>
 * <ul>
 *     <li>{@link SfcTaskWorkerProperties}：配置项；</li>
 *     <li>{@link SfcTaskWorkerClient}：出站内部协议客户端（经负载均衡按服务名定位 sfc-core）；</li>
 *     <li>{@link SfcTaskWorkerIdentity}：执行者身份；</li>
 *     <li>{@link SfcTaskWorkerRegistry}：任务类型能力与运行中任务注册表；</li>
 *     <li>{@link SfcTaskReporter} / {@link SfcTaskExecutionRunner}：进度日志上报与任务执行；</li>
 *     <li>{@link SfcTaskWorkerRuntime}：Worker 运行时生命周期；</li>
 *     <li>{@link SfcTaskInterruptController}：中断指令入站端点。</li>
 * </ul>
 *
 * <p>消费方也可以通过 {@code @EnableSfcTaskWorker} 显式引入本装配。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "sfc.task.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SfcTaskWorkerProperties.class)
public class SfcTaskWorkerAutoConfiguration {

    /**
     * 内部协议客户端：负责认领/上报/提交等出站调用，经负载均衡按服务名定位 sfc-core。
     *
     * @param properties           配置项
     * @param loadBalancerProvider 负载均衡选路器提供者（消费方未启用服务发现/负载均衡时为空）
     * @param issuerProvider       内部调用凭证签发器提供者（由 sfc-auth SDK 提供）
     * @return 内部协议客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcTaskWorkerClient sfcTaskWorkerClient(SfcTaskWorkerProperties properties,
                                                   ObjectProvider<LoadBalancerClient> loadBalancerProvider,
                                                   ObjectProvider<SfcInternalTokenIssuer> issuerProvider) {
        return new SfcTaskWorkerClient(properties, loadBalancerProvider, issuerProvider);
    }

    /**
     * 当前 worker 实例身份信息。
     *
     * @param properties    配置项
     * @param environment   Spring 环境
     * @param registrations 服务注册信息提供者（消费方未启用服务注册时为空）
     * @return 实例身份信息
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcTaskWorkerIdentity sfcTaskWorkerIdentity(SfcTaskWorkerProperties properties,
                                                       Environment environment,
                                                       ObjectProvider<Registration> registrations) {
        return new SfcTaskWorkerIdentity(properties, environment, registrations);
    }

    /**
     * 运行中任务注册表：自动扫描容器中的 {@link SfcTaskHandler} Bean 作为能力声明。
     *
     * @param handlerProvider 任务处理器提供者
     * @param properties      配置项
     * @return 注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcTaskWorkerRegistry sfcTaskWorkerRegistry(ObjectProvider<SfcTaskHandler> handlerProvider,
                                                       SfcTaskWorkerProperties properties) {
        return new SfcTaskWorkerRegistry(handlerProvider.orderedStream().toList(), properties);
    }

    /**
     * 执行上报器：进度节流上报、日志批量上报、结束/放弃上报。
     *
     * @param registry 运行中任务注册表
     * @param client   内部协议客户端
     * @param identity 当前实例身份信息
     * @return 执行上报器
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcTaskReporter sfcTaskReporter(SfcTaskWorkerRegistry registry,
                                           SfcTaskWorkerClient client,
                                           SfcTaskWorkerIdentity identity) {
        return new SfcTaskReporter(registry, client, identity);
    }

    /**
     * 任务执行器：在线程池线程中执行任务并上报结果。
     *
     * @param registry 运行中任务注册表
     * @param reporter 执行上报器
     * @return 任务执行器
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcTaskExecutionRunner sfcTaskExecutionRunner(SfcTaskWorkerRegistry registry,
                                                         SfcTaskReporter reporter) {
        return new SfcTaskExecutionRunner(registry, reporter);
    }

    /**
     * Worker 运行时生命周期。
     *
     * @param properties 配置项
     * @param client     内部协议客户端
     * @param registry   运行中任务注册表
     * @param runner     任务执行器
     * @param reporter   执行上报器
     * @param identity   当前实例身份信息
     * @param discoveryProvider 服务发现客户端提供者（消费方未启用服务发现时为空）
     * @return 运行时生命周期
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcTaskWorkerRuntime sfcTaskWorkerRuntime(SfcTaskWorkerProperties properties,
                                                     SfcTaskWorkerClient client,
                                                     SfcTaskWorkerRegistry registry,
                                                     SfcTaskExecutionRunner runner,
                                                     SfcTaskReporter reporter,
                                                     SfcTaskWorkerIdentity identity,
                                                     ObjectProvider<DiscoveryClient> discoveryProvider) {
        return new SfcTaskWorkerRuntime(properties, client, registry, runner, reporter, identity, discoveryProvider);
    }

    /**
     * 中断端点：接收 core 下发的任务中断指令。
     *
     * @param registry 运行中任务注册表
     * @return 中断端点控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcTaskInterruptController sfcTaskInterruptController(SfcTaskWorkerRegistry registry) {
        return new SfcTaskInterruptController(registry);
    }
}
