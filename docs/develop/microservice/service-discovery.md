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
| `group` | 分组，必须与 core 的 `sys.service.registry-group` 一致（默认 `SFC`） |
| `namespace` | 命名空间 ID。**public 的 ID 是空串，不要写 `public`** |
| `ephemeral` | 临时实例：停服即从注册中心消失，避免流量继续打到已死进程 |
| `metadata.sfc.api.prefix` | 服务自描述的 API 前缀，供网关拼接路径；缺省 `/api` |

## 3. core 侧配置

core 启用 Nacos 注册发现需要两个条件：

1. 启用 `sfc-ext-nacos` 插件；
2. 在配置中指定注册 provider：

```yaml
sys:
  service:
    # 默认 local（进程内注册表，未启用微服务模式）；nacos 需 sfc-ext-nacos 插件
    registry-provider: nacos
    # 注册到注册中心的自身服务名
    service-name: sfc-core
    # 注册中心分组，必须与各微服务的 group 一致
    registry-group: SFC
    nacos:
      server-addr: 127.0.0.1:8848
      namespace: ""
      # Nacos 服务端未开启鉴权时留空
      username: ""
      password: ""
```

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
