package com.sfc.demo;

import com.sfc.cloud.auth.SfcInternalAuthAutoConfiguration;
import com.sfc.cloud.auth.annotation.EnableSfcInternalAuth;
import com.sfc.cloud.task.worker.annotation.EnableSfcTaskWorker;
import com.sfc.demo.feign.DemoFeign;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * demo 微服务启动类。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableSfcInternalAuth
@EnableSfcTaskWorker
@EnableFeignClients(clients = DemoFeign.class)
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
