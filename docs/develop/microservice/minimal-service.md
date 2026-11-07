# 搭建最简的微服务

本文以一个只提供 HTTP 接口的最小服务为例，演示如何在 `microservices/` 下新增工程并接入注册中心与网关。

## 1. 创建模块目录

在 `microservices/` 下创建模块目录（示例名 `sfc-hello-service`），并把模块名加入 `microservices/pom.xml` 的 `<modules>`：

```xml
<modules>
    <module>sfc-common</module>
    <module>sfc-remote-client</module>
    <module>sfc-gateway</module>
    <module>sfc-demo-service</module>
    <!-- 新增模块 -->
    <module>sfc-hello-service</module>
</modules>
```

## 2. 编写 pom.xml

**推荐直接继承 `microservices`**，Spring Boot / Spring Cloud / Spring Cloud Alibaba 的版本与本目录的构建配置（Java 25、Lombok、编码等）全部由父 POM 单点管理，子模块只声明依赖：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.sfc.cloud</groupId>
        <artifactId>microservices</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <groupId>com.sfc.hello</groupId>
    <artifactId>sfc-hello-service</artifactId>
    <name>sfc-hello-service</name>
    <description>最简微服务示例</description>

    <dependencies>
        <!-- Web MVC 接口能力 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <!-- Nacos 服务注册发现 -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <!-- 共享库：信任头常量、远程调用等（按需） -->
        <dependency>
            <groupId>com.sfc.cloud</groupId>
            <artifactId>sfc-common</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

> 新增模块后需在仓库根目录执行 `mvn -pl sfc-auth install -DskipTests`，再构建本目录，详见[微服务概述](index.md)。

## 3. 编写启动类

最小启动类只需两个注解：注册发现客户端与 Spring Boot 启动类。

```java
package com.sfc.hello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class HelloServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(HelloServiceApplication.class, args);
    }
}
```

## 4. 编写 application.yml

所有注册参数都通过声明式配置完成，服务中不需要写任何注册代码。

```yaml
server:
  port: 8091

spring:
  application:
    # 服务名：注册到 Nacos 的名字，也是网关路由里的 {服务名}
    name: sfc-hello-service
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        # 必须与咸鱼云侧 spring.cloud.nacos.discovery.group 一致，否则"注册成功但互相看不见"
        group: SFC
        # 命名空间 public 的 ID 是空串，不要写 public
        namespace: ""
        ephemeral: true
```

## 5. 编写接口

业务接口统一放在 `/api/**` 下，与服务内其它接口的前缀约定保持一致：

```java
package com.sfc.hello.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hello")
public class HelloController {

    @GetMapping("world")
    public String world() {
        return "hello world";
    }
}
```

## 6. 构建与运行

```shell
# 只构建本模块（-am 会一并构建上游 sfc-common）
mvn -f microservices/pom.xml clean package -DskipTests -pl :sfc-hello-service -am

# 运行
java -jar microservices/sfc-hello-service/target/sfc-hello-service-1.0.0.jar
```

## 7. 验证

启动 Nacos 与本服务后：

- 直连服务：`GET http://127.0.0.1:8091/api/hello/world`
- 经过网关：`GET http://127.0.0.1:9090/api/service/sfc-hello-service/hello/world`

路径映射规则见[网关路由](gateway-routing.md)，注册结果核对见[服务发现与注册](service-discovery.md)。

> ⚠️ 微服务仅允许内网/本机暴露，对外入口只有网关。原因见[接口鉴权机制](authentication.md)。
