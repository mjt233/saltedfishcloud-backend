# 网关路由

`sfc-gateway` 是微服务模式下的整站唯一入口：浏览器只与网关对话，由网关把请求转发到 core 或各个微服务。

## 1. 映射规则

微服务路由由**服务发现自动生成**，网关配置只声明规则模板，不逐个硬编码服务路径。映射规则为：

```text
网关：/api/service/{服务名}/{服务接口}
服务：/api/{服务接口}
```

即：路径中的 `/api/service/{服务名}` 会被剥离，其余部分拼上 `/api` 后转发给对应服务。`{服务名}` 就是该服务 `spring.application.name` 的取值。

## 2. 配置项

网关配置位于 `microservices/sfc-gateway/src/main/resources/application.yml`：

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          discovery:
            locator:
              predicates:
                - name: Path
                  args:
                    pattern: "'/api/service/' + serviceId + '/**'"
              filters:
                - name: RewritePath
                  args:
                    regexp: "'/api/service/' + serviceId + '/?(?<remaining>.*)'"
                    replacement: "'/api/${remaining}'"
              enabled: true
```

- `predicates`：命中 `/api/service/{注册到 Nacos 的服务名}/**`；
- `filters`：`RewritePath` 把 `/api/service/{服务名}` 替换为 `/api`，完成"网关路径 → 服务路径"的改写。

保持 `enabled: true`，服务上线注册后路由自动生效，新增服务**无需修改网关配置**。

## 3. 访问示例

以 `sfc-demo-service` 为例，其控制器路径为 `/api/demo/whoami`：

| 访问方式 | 路径 |
|---|---|
| 直连服务 | `http://127.0.0.1:8090/api/demo/whoami` |
| 经过网关 | `http://127.0.0.1:9090/api/service/sfc-demo-service/demo/whoami` |

其中 `/api/service/sfc-demo-service` 被剥离，剩余 `demo/whoami` 补上 `/api` 后为 `/api/demo/whoami`。

## 4. 其它路由

`routes` 节点用于声明不依赖服务发现的显式路由，当前配置了一条把其余流量交给 `sfc-core` 的兜底路由：

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: core-route
              uri: lb://sfc-core
              predicates:
                - Path=/**
```

若某个服务需要固定路径（而不是 `/api/service/{服务名}` 形态），可在此追加 `id` / `uri`（`lb://{服务名}`）/ `predicates` 显式路由。

## 5. 注意事项

- **服务名即路由名**：服务名改了，网关路径也要改；服务未注册到 Nacos（或 group/namespace 不一致）时不会生成对应路由，请求将落到兜底路由（`sfc-core`）而非目标服务。
- **内部接口不可经网关访问**：`/api/internal/**` 由网关直接返回 403，该前缀只允许服务间直连调用，详见[接口鉴权机制](authentication.md)。
- **鉴权头由网关注入**：网关会对每个请求注入 `X-Sfc-Uid`（凭证无效时为 `0`）与 `X-From-Gateway`，微服务从这里读取当前用户。
- **注册参数两侧必须一致**：`group` / `namespace` 必须与 core 侧收敛为同一值，否则网关看不到目标实例，详见[服务发现与注册](service-discovery.md)。
