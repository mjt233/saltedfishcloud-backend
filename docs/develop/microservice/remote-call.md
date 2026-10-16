# 远程调用

微服务调用 sfc-core 的用户网盘文件操作，使用 `sfc-remote-client` 提供的 OpenFeign 客户端。该模块已配置好自动装配，消费方只需注入一个 bean。

## 1. 引入依赖

```xml
<dependency>
    <groupId>com.sfc.cloud</groupId>
    <artifactId>sfc-remote-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

`sfc-remote-client` 为**库**（非可运行进程），会传递引入 `sfc-common`、`spring-cloud-starter-openfeign` 与 `spring-cloud-starter-loadbalancer`。消费方需已注册到**同一个** Nacos（`group=SFC`、`namespace=public`），否则无法按服务名寻址。

> SDK 的自动装配已通过 `META-INF/spring/...AutoConfiguration.imports` 就绪，消费方**不需要**自己写 `@EnableFeignClients`。

## 2. 调用网盘文件接口

唯一入口是 `com.sfc.cloud.remote.client.SfcCoreFileClient`，注入后即可像调用本地方法一样使用：

```java
@Service
@RequiredArgsConstructor
public class MyDiskService {

    /** 唯一入口：列表 / 搜索 / 下载 / 上传 / 重命名 / 复制 / 移动 / 删除都是同一个 bean */
    private final SfcCoreFileClient fileClient;

    /** 列出 uid=7 的 /docs 目录 */
    public List<SfcFileInfo> list() {
        return fileClient.list(7, "/docs").getDataOrThrow();
    }

    /** 在 uid=7 的网盘内全盘搜索文件名含"报表"的内容（page 从 0 开始，size 上限 100） */
    public SfcPageInfo<SfcFileInfo> search() {
        return fileClient.search(7, "报表", 0, 10).getDataOrThrow();
    }

    /** 上传本地文件（流式：边读边发，不整体进内存） */
    public Long upload(Path file) {
        return fileClient.upload(new FileSystemResource(file), 7, "/docs", null, null).getDataOrThrow();
    }

    /** 复制 / 移动 / 重命名 / 删除 */
    public void others() {
        fileClient.copy(7, 7, 7, "/src", "/dst", true, List.of("a.txt")).requireSuccess();
        fileClient.move(7, 0, 7, "/pub", "/mine", false, List.of("p.txt")).requireSuccess();
        fileClient.rename(7, "/docs", "old.txt", "new.txt").requireSuccess();
        fileClient.delete(7, "/docs", List.of("x.txt")).requireSuccess();
    }
}
```

方法一览：

| 方法 | 说明 |
|---|---|
| `list(uid, path)` | 列出目录下的文件与目录 |
| `search(uid, keyword, page, size)` | 在指定用户网盘内全盘模糊搜索 |
| `download(uid, path)` | 下载，返回 `feign.Response`，调用方负责消费并关闭 |
| `upload(file, uid, path, name, mtime)` | 流式上传，返回保存结果的响应信封 |
| `rename(uid, path, oldName, newName)` | 重命名 |
| `copy(uid, sourceUid, targetUid, sourcePath, targetPath, overwrite, names)` | 复制 |
| `move(uid, sourceUid, targetUid, sourcePath, targetPath, overwrite, names)` | 移动 |
| `delete(uid, path, names)` | 删除 |

`uid = 0` 表示公共网盘，写入公共网盘需要管理员权限。

## 3. 可选配置

```yaml
sfc:
  remote:
    client:
      # 连接超时，默认 5s
      connect-timeout: 5s
      # 读取超时，默认 30m；0 表示不限制（大文件上传建议放宽）
      read-timeout: 30m
```

超时、重试、日志级别沿用 Spring Cloud OpenFeign 标准配置，例如 `spring.cloud.openfeign.client.config.sfc-core.read-timeout=60000`。

## 4. 调用其它微服务

`sfc-remote-client` 只封装了对 core 的调用。调用**其它微服务**时按标准 OpenFeign 方式自行声明接口即可，参考 `sfc-demo-service`：

```java
@FeignClient(name = "sfc-demo-service", path = "/api/internal")
public interface DemoFeign {

    @GetMapping("hello")
    String hello();
}
```

并在启动类上显式指定要扫描的客户端：

```java
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(clients = DemoFeign.class)
public class DemoServiceApplication {
    // ...
}
```

调用内部接口需要携带服务间调用凭证，见[接口鉴权机制](authentication.md)。

## 5. 错误处理

core 的业务错误同时写进 HTTP 状态码与响应信封，SDK 统一转为 `SfcRemoteApiException`：

| 场景 | 行为 |
|---|---|
| HTTP 非 2xx（400/403/404/422/500） | 解析信封后抛 `SfcRemoteApiException`（含 `httpStatus` / `code` / `businessCode` / `msg`） |
| HTTP 2xx 但信封 `code != 200` | `requireSuccess()` / `getDataOrThrow()` 抛同一异常 |
| 无凭证或凭证失效 | core 返回 403 + 信封；可用 `isForbidden()` 判断 |

> ⚠️ 身份与凭证由 SDK 自动透传：调用时会带上当前入站请求的用户凭证，**不要**在日志中记录凭证值。详见[接口鉴权机制](authentication.md)。
