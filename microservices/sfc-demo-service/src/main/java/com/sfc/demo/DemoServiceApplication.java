package com.sfc.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * demo 微服务启动类。
 * <p>
 * <b>本类的存在意义是"验证"，不是"功能"</b>：它位于咸鱼云仓库的 {@code microservices/} 子目录，但
 * <b>不依赖咸鱼云任何模块</b>（既不继承 root pom，也不在 root {@code <modules>} 中），
 * 用来回答改造之初的那个问题——<b>"后续新微服务单独开发可以吗"</b>。
 * <p>
 * <b>注册是怎么发生的</b>：本类里<b>没有一行注册代码</b>。Nacos 的注册由
 * {@code spring-cloud-starter-alibaba-nacos-discovery} 在自动配置阶段完成（声明式），
 * 参数全部来自 {@code application.yml} 的 {@code spring.cloud.nacos.discovery.*}。
 * 这与咸鱼云侧"手写 SPI provider + 裸 nacos-client"形成刻意的对照：
 * <b>同一个注册中心，两种接入方式</b>，两者能互相看见即证明契约与协议实现都正确。
 * <p>
 * <b>为什么需要 Web 容器</b>：本 demo 刻意不提供任何业务端点（极简探针），
 * Web starter 在这里的唯一作用是提供常驻线程，否则应用在注册完成后会立即退出。
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
