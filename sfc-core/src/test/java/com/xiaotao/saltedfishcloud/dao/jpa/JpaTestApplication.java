package com.xiaotao.saltedfishcloud.dao.jpa;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 用于 JPA 切片测试（{@code @DataJpaTest}）的轻量级 Spring Boot 入口配置。
 * <p>
 * 仅扫描实体与 Repository 所在的包，不启用任务调度、RPC、压缩包等附加模块，
 * 避免加载 {@code AsyncTaskAutoConfiguration} 等需要完整运行时依赖的组件。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {})
@EntityScan("com.xiaotao.saltedfishcloud.model")
@EnableJpaRepositories("com.xiaotao.saltedfishcloud.dao.jpa")
@EnableJpaAuditing
public class JpaTestApplication {
}
