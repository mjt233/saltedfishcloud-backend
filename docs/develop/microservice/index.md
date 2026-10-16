# 微服务概述

咸鱼云的微服务工程统一放在仓库根目录的 `microservices/` 下，用于把部分后端能力拆分为独立进程开发、部署。

这些工程与仓库内其它模块有两个关键区别：

- **不继承 root `pom.xml`**：父 POM 是 `microservices/pom.xml`，而它的父 POM 才是 `spring-boot-starter-parent`，因此不会被拖入咸鱼云宿主约 200+ 个 jar 的技术栈；
- **零宿主依赖**：不依赖任何 `com.xiaotao.saltedfishcloud:*` 坐标。允许的仓库内依赖是两个**共享库**：`com.sfc.cloud:sfc-common`（轻量：常量/信封/异常/工具/分页/远程契约）与 `com.sfc.cloud:sfc-common-data`（JPA 基座）。⚠️ 二者已于 Phase 1 **上提到仓库根目录**（`sfc-common/`、`sfc-common-data/`），宿主的 `sfc-api` 也依赖它们——因此它们**不继承任何 parent**，自带 BOM，`sfc-common` 还必须保持零 JPA 依赖（网关无 JPA）。

## 工程清单

| 工程 | 作用 |
|---|---|
| [`sfc-common`](../../../sfc-common/) | 共享库（非可运行进程，**位于仓库根目录**）：信任头常量、用户凭证解析、远程调用契约模型，以及 Phase 1 迁出的响应信封 `SfcJsonResult`、异常族、常量、字符串/JSON 工具、分页模型 `SfcPageInfo`/`SfcPageableRequest` |
| [`sfc-common-data`](../../../sfc-common-data/) | 共享库（非可运行进程，**位于仓库根目录**）：JPA 数据访问基座（实体基类、`SfcBaseRepo`、`SfcCrudService(Impl)`、雪花主键、`SfcDBUtils`、`@SfcUID`），身份与事务经 SPI 注入 |
| `sfc-remote-client` | 远程客户端 SDK（库）：调用 sfc-core 网盘文件操作的 OpenFeign 声明式接口 |
| `sfc-gateway` | 独立网关（Spring Cloud Gateway Server WebFlux）：整站唯一入口与微服务代理 |
| `sfc-demo-service` | 微服务示例：演示最简工程结构、注册发现、远程调用与接口鉴权 |

依赖方向：`sfc-common` → `sfc-common-data` / `sfc-remote-client` / `sfc-gateway` / `sfc-demo-service`。

## 构建前置

共享库 `sfc-common` / `sfc-common-data` 与内部接口鉴权 SDK `sfc-auth` **都不继承根 pom**，但都已由根 `<modules>` 聚合。因此**推荐从仓库根做单次 reactor 构建**（reactor 内部会互相解析 `target` 产物，无需 `install`）：

```shell
mvn clean package -DskipTests
```

⚠️ **不要**再单独构建 `microservices/pom.xml`：`sfc-common` 已不在其 reactor 内，那样会从**本地仓库**解析，可能拿到旧快照（静默错误）。同理不要用 `-rf` 从中途恢复。

## 学习路径

1. [搭建最简的微服务](minimal-service.md) —— 从零建一个可注册、可被网关访问的服务；
2. [网关路由](gateway-routing.md) —— 网关如何把请求转发到服务接口；
3. [服务发现与注册](service-discovery.md) —— Nacos 的基本配置；
4. [远程调用](remote-call.md) —— 用 OpenFeign 调用 sfc-core；
5. [接口鉴权机制](authentication.md) —— 用户凭证透传与服务间内部接口鉴权。
