# sfc-local-registry-client

`spring.cloud.sfc-local.*` 配置的 local 注册中心客户端 SDK，为微服务提供不依赖 Nacos 的服务注册与发现。

## 依赖

```xml
<dependency>
    <groupId>com.sfc.cloud</groupId>
    <artifactId>sfc-local-registry-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

本模块不继承仓库根 pom，需先 install：

```shell
mvn -pl sfc-local-registry-client install -DskipTests
```

## 配置

```yaml
spring:
  application:
    name: sfc-hello-service
  cloud:
    sfc-local:
      server-addr: 127.0.0.1:8087
      password: ${SFC_LOCAL_REGISTRY_PASSWORD:}
      heartbeat-interval: 10s
      metadata:
        sfc.api.prefix: /api
```

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用；`server-addr` 为空时不会创建任何 Bean |
| `server-addr` | - | 注册中心地址，支持 `host:port` 或 `http(s)://host:port` |
| `password` | - | 与服务端 `sys.service.local-registry.password` 一致 |
| `service-name` | `spring.application.name` | 服务名 |
| `instance-id` | `host:port` | 实例 ID |
| `host` | 本机地址 | 注册 host |
| `secure` | `false` | 是否使用 HTTPS |
| `context-path` | 空 | 注册中心上下文路径 |
| `metadata` | 空 | 实例元数据 |
| `heartbeat-interval` | `10s` | 心跳间隔，应小于服务端心跳超时 |
| `connect-timeout` | `3s` | 连接超时 |
| `read-timeout` | `5s` | 读超时 |

## 行为

- 自动配置入口 `com.sfc.cloud.local.registry.config.SfcLocalRegistryAutoConfiguration` 只在 `server-addr` 已配置且未禁用时生效，Bean 装配由同包的 `SfcLocalRegistryConfiguration` 承担；
- Web 端口绑定后通过 Spring Cloud `AbstractAutoServiceRegistration` 自动注册；
- 守护线程定时心跳；core 重启导致实例丢失时自动重新注册；
- 实现 `DiscoveryClient`，可直接注入使用；需要 `lb://` 时额外引入 `spring-cloud-starter-loadbalancer`；
- 关闭时先尽力注销，再停止心跳线程。

## 服务端接口

均为 `/api/local-registry/**`，需要请求头 `X-Sfc-Local-Registry-Password`：

- `POST /register`
- `POST /heartbeat`
- `POST /deregister`
- `GET /instances?serviceId=`
- `GET /services`

接口由 `sfc-core` 在 `sys.service.registry-provider=local` 时提供，客户端应直连 core。
