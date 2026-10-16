# 微服务概述

咸鱼云的微服务工程统一放在仓库根目录的 `microservices/` 下，用于把部分后端能力拆分为独立进程开发、部署。

这些工程与仓库内其它模块有两个关键区别：

- **不继承 root `pom.xml`**：父 POM 是 `microservices/pom.xml`，而它的父 POM 才是 `spring-boot-starter-parent`，因此不会被拖入咸鱼云宿主约 200+ 个 jar 的技术栈；
- **零宿主依赖**：不依赖任何 `com.xiaotao.saltedfishcloud:*` 坐标，唯一允许的仓库内依赖是本目录下的共享库 `com.sfc.cloud:sfc-common`。

## 工程清单

| 工程 | 作用 |
|---|---|
| `sfc-common` | 共享库（非可运行进程）：信任头常量、用户凭证解析、远程调用契约模型 |
| `sfc-remote-client` | 远程客户端 SDK（库）：调用 sfc-core 网盘文件操作的 OpenFeign 声明式接口 |
| `sfc-gateway` | 独立网关（Spring Cloud Gateway Server WebFlux）：整站唯一入口与微服务代理 |
| `sfc-demo-service` | 微服务示例：演示最简工程结构、注册发现、远程调用与接口鉴权 |

依赖方向：`sfc-common` → `sfc-remote-client` / `sfc-gateway` / `sfc-demo-service`。

## 构建前置

内部接口鉴权 SDK `sfc-auth` 在**仓库根 reactor** 中且不继承根 pom，单独构建微服务前需先安装到本地仓库：

```shell
mvn -pl sfc-auth install -DskipTests
```

聚合构建（在仓库根目录执行）：

```shell
mvn -f microservices/pom.xml clean package -DskipTests
```

## 学习路径

1. [搭建最简的微服务](minimal-service.md) —— 从零建一个可注册、可被网关访问的服务；
2. [网关路由](gateway-routing.md) —— 网关如何把请求转发到服务接口；
3. [服务发现与注册](service-discovery.md) —— Nacos 的基本配置；
4. [远程调用](remote-call.md) —— 用 OpenFeign 调用 sfc-core；
5. [接口鉴权机制](authentication.md) —— 用户凭证透传与服务间内部接口鉴权。
