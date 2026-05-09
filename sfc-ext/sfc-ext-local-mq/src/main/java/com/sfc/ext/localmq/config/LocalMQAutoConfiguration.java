package com.sfc.ext.localmq.config;

import com.sfc.ext.localmq.core.LocalMQService;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地 {@link MQService} 插件自动配置。
 * <p>
 * 当 {@code sys.service.mq-provider=local} 时，由插件侧提供本地内存消息队列实现。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "sys.service", name = "mq-provider", havingValue = "local")
public class LocalMQAutoConfiguration {

    /**
     * 注册本地 MQ 实现。
     *
     * @return 本地 MQ 服务实现，容器关闭时会显式停止本地消费线程
     */
    @Bean(destroyMethod = "shutdown")
    public MQService localMqService() {
        log.info("=========== 正在使用本地基于内存的 MQ 实现，适用于单实例部署 ===========");
        return new LocalMQService();
    }
}
