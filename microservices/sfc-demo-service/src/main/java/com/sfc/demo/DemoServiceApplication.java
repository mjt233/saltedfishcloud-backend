package com.sfc.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * demo 微服务启动类。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class DemoServiceApplication {

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    static void main(String[] args) {
        SpringApplication.run(DemoServiceApplication.class, args);
    }
}
