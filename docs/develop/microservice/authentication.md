# 接口鉴权机制

微服务模式下有两类身份：**用户身份**（谁在访问）与**服务身份**（是不是集群内的服务）。本文先讲这两件事怎么配置、怎么用，最后再说明背后的机制。

## 1. 使用 sfc-auth

`sfc-auth` 是内部接口（服务间调用）鉴权的 SDK，core 与微服务共用。参考实现：`microservices/sfc-demo-service`。

### 1.1 引入依赖

```xml
<dependency>
    <groupId>com.xiaotao</groupId>
    <artifactId>sfc-auth</artifactId>
    <version>${auth.version}</version>
</dependency>
```

`${auth.version}` 在 `microservices/pom.xml` 中声明，需与仓库根 `pom.xml` 的 `auth.version` 保持一致。

> ⚠️ `sfc-auth` 在仓库根 reactor 中且不继承根 pom，构建微服务前需先执行 `mvn -pl sfc-auth install -DskipTests`。

### 1.2 服务端：启用校验

在启动类上加 `@EnableSfcInternalAuth`：

```java
@SpringBootApplication
@EnableDiscoveryClient
@EnableSfcInternalAuth
public class DemoServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(DemoServiceApplication.class, args);
    }
}
```

然后把**只允许服务间调用**的接口放在 `/api/internal/**` 下即可，无需在方法上做额外处理（该前缀的含义见下文第 3 节）：

```java
@RestController
@RequestMapping("/api/internal")
public class InternalController {

    @RequestMapping("/hello")
    @ResponseBody
    public String hello() {
        return "Hello World";
    }
}
```

配好共享密钥（core 与所有微服务必须完全一致）：

```yaml
sfc:
  internal:
    key: ${SFC_INTERNAL_KEY:}
```

### 1.3 客户端：调用内部接口

同时满足以下三个条件时，签发服务凭证的 Feign 拦截器 `SfcInternalTokenFeignInterceptor` 会由 `sfc-remote-client` 的自动装配**自动注册**，消费方**无需手动注册**：

1. 引入了 `sfc-auth` 依赖；
2. 启动类开启了 `@EnableSfcInternalAuth`（提供凭证签发器）；
3. 引入了 `sfc-remote-client` 依赖（自动装配检测到签发器后注册拦截器）。

因此调用内部接口只需按标准 OpenFeign 声明接口并让启动类扫描到它：

```java
@FeignClient(name = "sfc-demo-service", path = "/api/internal")
public interface DemoFeign {

    @GetMapping("hello")
    String hello();
}
```

```java
@SpringBootApplication
@EnableDiscoveryClient
@EnableSfcInternalAuth
@EnableFeignClients(clients = DemoFeign.class)
public class DemoServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(DemoServiceApplication.class, args);
    }
}
```

调用 core 的网盘文件接口同样由自动装配完成，直接注入 `SfcCoreFileClient` 即可，见[远程调用](remote-call.md)。

### 1.4 需要手动注册的场景

自动注册依赖 `sfc-remote-client` 的自动装配，且注册出来的是**全局**拦截器。以下两种场景需要手动注册：

- **未引入 `sfc-remote-client`**：服务只想调用内部接口，不需要 core 的网盘文件 SDK，此时不存在自动装配，必须自行注册；
- **只想让凭证发往特定客户端**：不希望所有 Feign 客户端都携带内部凭证，改用客户端级配置限定范围。

手动注册的方式是在 `@FeignClient` 的 `configuration` 中声明拦截器 bean：

```java
public class InternalTokenFeignConfig {

    @Bean
    public RequestInterceptor sfcInternalTokenInterceptor(SfcInternalTokenIssuer issuer) {
        return new SfcInternalTokenFeignInterceptor(issuer);
    }
}
```

```java
@FeignClient(name = "sfc-demo-service", path = "/api/internal",
        configuration = InternalTokenFeignConfig.class)
public interface DemoFeign {

    @GetMapping("hello")
    String hello();
}
```

使用 `RestClient`（而非 Feign）调用内部接口时，改用 SDK 的初始化器 `SfcInternalTokenRequestInitializer`（`issuer` 由容器注入；用初始化器而非拦截器可避免请求体被整体缓冲，大文件上传不会 OOM）：

```java
RestClient restClient = RestClient.builder()
        .baseUrl("lb://sfc-core")
        .requestInitializers(initializers -> initializers.add(new SfcInternalTokenRequestInitializer(issuer)))
        .build();
```

> 手动注册同样有前置条件：必须引入 `sfc-auth` 依赖并开启 `@EnableSfcInternalAuth`，否则 `SfcInternalTokenIssuer` 不存在，无法签发凭证。

### 1.5 配置项

前缀为 `sfc.internal`：

| 配置项 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `key` | `SFC_INTERNAL_KEY` | 空 | 共享密钥。留空 = fail-closed（服务端拒绝全部内部接口，客户端无法签发）；不足 32 字符 = 启动失败 |
| `service-name` | — | `spring.application.name` | 写入凭证的 `iss`，便于服务端审计/白名单 |
| `token-ttl` | — | `5m` | 凭证有效期 |
| `clock-skew` | — | `30s` | 允许的时钟偏移 |
| `server-enabled` | — | `true` | 是否在本进程启用服务端校验过滤器 |
| `allowed-issuers` | — | 空 | 调用方服务名白名单；空 = 不校验 |

### 1.6 验证

```shell
# 1. 经网关访问内部接口：应返回 403
curl -i http://127.0.0.1:9090/api/internal/file/v2/list

# 2. 直连服务访问内部接口：未带 X-Sfc-Internal-Token 应返回 403
curl -i http://127.0.0.1:8090/api/internal/hello

# 3. 服务间调用：经示例接口验证（demo 服务用 Feign 调用自身的内部接口）
curl http://127.0.0.1:9090/api/service/sfc-demo-service/demo/testInternal
```

## 2. 用户凭证透传机制

网关是**唯一身份来源**：

1. 网关从请求中取出用户凭证——依次尝试请求头 `Token`、查询参数 `Token`、Cookie `token`；
2. 用环境变量 `SFC_JWT_SECRET` **本地验签**，取值必须与 core 的配置项 `sys.safe.token` 一致；
3. 验签通过则注入 `X-Sfc-Uid = 用户ID`，否则注入 `X-Sfc-Uid = 0`（匿名），同时注入 `X-From-Gateway: sfc-gateway`；
4. 用户凭证**原值透传**给下游，微服务如需"登出即失效"级别的判定，可自行完整校验。

微服务从信任头常量 `com.sfc.cloud.trust.SfcTrustHeaders` 读取当前用户，不要写字面量：

```java
@GetMapping("/whoami")
public Map<String, Object> whoami(HttpServletRequest request) {
    Map<String, Object> result = new LinkedHashMap<>();
    String rawUid = request.getHeader(SfcTrustHeaders.UID);
    result.put("uid", rawUid);
    result.put("uidSource", SfcTrustHeaders.UID);
    return result;
}
```

| 头名 | 含义 |
|---|---|
| `X-Sfc-Uid` | 当前用户 ID；`0` 表示匿名（与公共资源 `uid=0` 的约定一致） |
| `X-From-Gateway` | 固定取值 `sfc-gateway`，标识请求来自网关 |

> ⚠️ 这是框架期的**信任模式**：微服务不校验 `X-Sfc-Uid`，因此**微服务只允许内网/本机暴露**，对外入口只有网关；凭证是敏感数据，**不得写入日志或响应体**。

## 3. 服务间内部接口鉴权

只允许服务间调用的接口统一放在 `/api/internal/**` 前缀下，用"共享密钥 + 每请求自签 JWT"证明调用方是集群内的服务：

```text
部署配置（core 与所有微服务同一份）：SFC_INTERNAL_KEY ← sfc.internal.key，密钥永不上线传输
调用：请求头 X-Sfc-Internal-Token: <每次调用新签发的 HS256 JWT>
服务端：/api/internal/** 校验「签名 + 有效期（含时钟偏移）+ 必需声明」→ 不符返回 403
网关：/api/internal/** 一律 403（不进路由链），并剥离客户端自带的 X-Sfc-Internal-Token
```

规则要点：

- 服务间调用走 `lb://sfc-core` 直连，**不经网关**，因此网关整前缀拒绝不会误伤合法调用；
- 未配置密钥时**一律拒绝**（fail-closed），调用方也无法签发凭证；
- 密钥长度不足 32 字符时服务启动失败；
- 网关凭证密钥 `SFC_JWT_SECRET` 与内部接口密钥 `SFC_INTERNAL_KEY` 是**两把不同的密钥**，不要混用。

## 4. 已知局限

`sfc-auth` 是框架期的**兜底**，不是完整的服务间鉴权：

- 静态共享密钥，不区分服务身份；
- **不做防重放**，同一凭证在有效期内可重复使用，缓解手段只有调小 `token-ttl`；
- 轮换密钥需要 core 与所有微服务同时重启。
