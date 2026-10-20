# 服务发现与注册

咸鱼云使用 Nacos 作为注册中心。core 与所有微服务注册到**同一分组、同一命名空间**后即可互相发现。

## 1. 启动 Nacos

本地开发直接以单机模式启动 Nacos 3.x：

- 控制台：`http://127.0.0.1:8080`
- 服务端 API 与客户端连接端口：`8848`
- 客户端 gRPC 端口：`9848`（必须放通）

## 2. 微服务侧配置

在服务的 `application.yml` 中声明注册信息，**无需任何注册代码**：

```yaml
spring:
  application:
    name: sfc-hello-service
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        group: SFC
        namespace: ""
        ephemeral: true
        metadata:
          sfc.api.prefix: /api
          sfc.version: 1.0.0
```

| 配置项 | 说明 |
|---|---|
| `spring.application.name` | 注册到 Nacos 的服务名，也是网关路由中的 `{服务名}` |
| `server-addr` | Nacos 服务端地址，多节点用逗号分隔 |
| `group` | 分组，必须与 core 的 `spring.cloud.nacos.discovery.group` 一致（默认 `SFC`） |
| `namespace` | 命名空间 ID。**public 的 ID 是空串，不要写 `public`** |
| `ephemeral` | 临时实例：停服即从注册中心消失，避免流量继续打到已死进程 |
| `metadata.sfc.api.prefix` | 服务自描述的 API 前缀，供网关拼接路径；缺省 `/api` |

## 3. core 侧配置

core 通过 `sys.service.registry-provider` 选择注册 provider：`local`（默认，进程内注册表）或 `nacos`。
Nacos 能力已内置，服务名与连接参数使用标准 Spring 配置：

```yaml
spring:
  # 注册到 Nacos 的服务名
  application:
    name: sfc-core
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        # 必须与各微服务的 group 一致
        group: SFC
        namespace: ""
        # Nacos 服务端未开启鉴权时留空
        username: ""
        password: ""

sys:
  service:
    registry-provider: nacos
```

`registry-provider: local` 时 Nacos 自动配置整体关闭，不会创建客户端或尝试连接注册中心。
配置为 `nacos` 时若注册中心不可达，启动会快速失败。

部署侧的完整默认值见 [配置参数](../../quick-start/config.md)。

## 4. 验证注册结果

先查 Nacos 服务端，再查业务侧。服务端 API 查询（Nacos 3.x 路径）：

```shell
curl "http://127.0.0.1:8848/nacos/v3/client/ns/instance/list?serviceName=sfc-core&groupName=SFC&namespaceId=public"
curl "http://127.0.0.1:8848/nacos/v3/client/ns/instance/list?serviceName=sfc-hello-service&groupName=SFC&namespaceId=public"
```

也可以直接打开 Nacos 控制台的「服务管理 → 服务列表」查看。

排查顺序：

- Nacos 里没有实例 = **没注册上**（检查 `server-addr`、启动日志）；
- Nacos 里有、但对方发现不了 = **查不到**（几乎都是 `group` / `namespace` 两侧不一致）；
- 服务停止后实例应在数秒内自动消失（`ephemeral: true`），否则检查是否用了持久化实例。

## 5. local 注册中心与外部服务注册

未启用 Nacos 时，`registry-provider: local` 也支持跨进程注册与发现：core 在 `/api/local-registry/**` 暴露带密码保护的注册接口，外部服务通过心跳续约，超时未续约的实例会被自动移除。服务进程由使用者自行启动。

### 5.1 core 配置

```yaml
sys:
  service:
    registry-provider: local
    local-registry:
      # 外部注册密码。留空表示禁用外部注册接口
      password: ${SFC_LOCAL_REGISTRY_PASSWORD:}
      # 心跳超时时间
      heartbeat-timeout: 30s
      # 过期实例清理间隔
      cleanup-interval: 10s
```

密码通过请求头 `X-Sfc-Local-Registry-Password` 传递，注册、心跳、注销与发现接口都会校验。密码为空时接口返回 403（fail-closed）。

### 5.2 客户端 SDK

微服务引入 `com.sfc.cloud:sfc-local-registry-client`：

```xml
<dependency>
    <groupId>com.sfc.cloud</groupId>
    <artifactId>sfc-local-registry-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

配置：

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

SDK 会在 Web 端口绑定后自动注册，并按 `heartbeat-interval` 发送心跳；core 重启导致实例丢失时会自动重新注册。`server-addr` 为空时 SDK 不创建任何 Bean，无需 `@EnableDiscoveryClient`。需要使用 `lb://服务名` 调用其他服务时，额外引入 `spring-cloud-starter-loadbalancer`。

| 配置项 | 说明 |
|---|---|
| `server-addr` | 注册中心地址，支持 `host:port` 或 `http(s)://host:port` |
| `password` | 与服务端 `sys.service.local-registry.password` 一致 |
| `service-name` | 服务名，缺省取 `spring.application.name` |
| `instance-id` | 实例 ID，缺省为 `host:port` |
| `host` | 注册 host，缺省取本机地址 |
| `metadata` | 实例元数据 |
| `heartbeat-interval` | 心跳间隔，应小于服务端心跳超时 |
| `context-path` | 注册中心上下文路径，缺省为空 |

### 5.3 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/local-registry/register` | 注册实例，body 含 `serviceId/instanceId/host/port/secure/metadata` |
| POST | `/api/local-registry/heartbeat` | 心跳续约，body 含 `serviceId/instanceId` |
| POST | `/api/local-registry/deregister` | 注销实例 |
| GET | `/api/local-registry/instances?serviceId=` | 查询实例列表 |
| GET | `/api/local-registry/services` | 查询服务名列表 |

所有接口都需要密码请求头。接口独立于 `/api/internal/**`，不会被服务间鉴权过滤器拦截；客户端应直连 core。`metadata` 会原样写入注册表，供管理端与 `sfc.plugin.info` / `sfc.plugin.autoload` 等聚合逻辑使用。core 拉取 `sfc.plugin.autoload` 资源时会附带内部调用凭证，需要配置 `sfc.internal.key`（环境变量 `SFC_INTERNAL_KEY`）。

### 5.4 限制

- 注册表位于 core 进程内存中，core 重启后外部实例会短暂消失，由客户端自动重新注册；
- 仅适合 core 与外部服务网络可达的部署（通常为同机或内网）；
- 没有主动健康检查，进程僵死时依赖心跳 TTL 清理；
- 多个 core 实例之间不共享注册表，需要跨节点共享时使用 Nacos。
