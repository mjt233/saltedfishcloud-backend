package com.sfc.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 咸鱼云独立网关的启动类（Spring Cloud Gateway · Server WebFlux）。
 *
 * @author xiaotao
 */
@SpringBootApplication
public class SfcGatewayApplication {

    /**
     * 进程入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SfcGatewayApplication.class, args);
    }
}
