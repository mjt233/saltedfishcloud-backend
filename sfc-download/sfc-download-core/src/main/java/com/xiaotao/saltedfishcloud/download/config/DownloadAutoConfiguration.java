package com.xiaotao.saltedfishcloud.download.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan("com.xiaotao.saltedfishcloud.download.model")
@EnableJpaRepositories(basePackages = "com.xiaotao.saltedfishcloud.download.repo")
public class DownloadAutoConfiguration {
}
