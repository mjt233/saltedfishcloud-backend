package com.sfc.team;

import com.sfc.team.model.TeamProperty;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 团队空间插件 Spring Boot 自动配置。
 * <p>
 * 负责扫描插件组件与实体、启用插件级 JPA 仓库，并注册插件特性开关。
 */
@Configuration
@ComponentScan("com.sfc.team")
@EntityScan("com.sfc.team.model")
@EnableJpaRepositories(basePackages = "com.sfc.team.repo")
public class TeamAutoConfiguration {

    /**
     * 注册团队空间配置属性并挂载特性开关
     *
     * @param configService 配置服务（绑定插件配置实体）
     * @param helloService  特性服务（挂载 team 特性名）
     * @return 团队空间配置实体
     */
    @Bean
    public TeamProperty teamProperty(ConfigService configService, HelloService helloService) {
        TeamProperty property = new TeamProperty();
        configService.bindPropertyEntity(property);
        helloService.setFeature("team", property);
        return property;
    }
}
