# sfc-remote-client —— 微服务回调 sfc-core 的远程客户端 SDK

> 把 core 的网盘文件操作变成微服务里的普通方法调用，**唯一入口是 `SfcCoreFileClient` 一个 bean**：
> **获取列表 / 搜索 / 下载 / 上传 / 重命名 / 复制 / 移动 / 删除**。
> 本模块是**库**（不是可运行进程）：不继承 root `pom.xml`、零宿主依赖、不产出可执行 jar。

---

---

## 快速上手

```xml

<dependency>
    <groupId>com.sfc.cloud</groupId>
    <artifactId>sfc-remote-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

自动装配已就绪（`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`），
消费方**不需要**自己写 `@EnableFeignClients`：

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

    /** 上传内存字节：ByteArrayResource 自身没有文件名，必须显式传 name */
    public Long uploadBytes(byte[] content) {
        return fileClient.upload(new ByteArrayResource(content), 7, "/docs", "hello.txt", null).getDataOrThrow();
    }

    /** 复制到同用户目录；跨用户时把两个 uid 传成不同值 */
    public void copy() {
        fileClient.copy(7, 7, 7, "/src", "/dst", true, List.of("a.txt", "b.txt")).requireSuccess();
    }

    /** 移动（剪切/粘贴）：从公共网盘移到自己的网盘 */
    public void move() {
        fileClient.move(7, 0, 7, "/pub", "/mine", false, List.of("p.txt")).requireSuccess();
    }

    /** 重命名与删除 */
    public void others() {
        fileClient.rename(7, "/docs", "old.txt", "new.txt").requireSuccess();
        fileClient.delete(7, "/docs", List.of("x.txt")).requireSuccess();
    }

    /** 下载（原样返回 feign.Response，调用方负责消费并关闭响应体） */
    public byte[] download() throws IOException {
        try (Response response = fileClient.download(7, "/docs/a.txt");
             InputStream in = response.body().asInputStream()) {
            return in.readAllBytes();
        }
    }
}
```

---

## 配置项

| 配置键                                 | 默认值    | 说明                                                            |
|-------------------------------------|--------|---------------------------------------------------------------|
| `sfc.remote.client.connect-timeout` | `5s`   | 流式上传的 JDK HttpClient 连接超时                                     |
| `sfc.remote.client.read-timeout`    | `30m`  | 流式上传的读取超时；`0` 表示不限制                                           |

超时、重试、日志级别沿用 Spring Cloud OpenFeign 标准配置，例如
`spring.cloud.openfeign.client.config.sfc-core.read-timeout=60000`。

> ⚠️ 服务发现寻址要求消费方注册到**同一个** Nacos（`group=SFC`、`namespace=public`）

---

## 身份与凭证

| 项          | 做法                                                                                                                         |
|------------|----------------------------------------------------------------------------------------------------------------------------|
| 身份来源       | 网关本地验签后注入 `X-Sfc-Uid`，并把用户凭证原值透传给微服务                                                                                       |
| 本 SDK      | `CredentialForwardingInterceptor` 把当前入站请求的 `Token`（请求头 → 查询参数 → `token` Cookie）与 `X-Sfc-Uid` 原样带到回调请求上；**不产生、不解析、不校验**任何凭证 |
| 不打印        | 凭证是敏感数据：日志只记录「是否携带」，不记录其值                                                                                                  |
| 无 HTTP 上下文 | 定时任务/消息消费等线程**不伪造身份**，回调以匿名发起，需要登录的操作会被 core 拒绝（403）                                                                       |

> 🔒 框架期为「信任模式」：微服务与内部接口**只允许内网/本机暴露**，对外入口只有 `sfc-gateway`。

---

## 错误语义

core 的业务错误**同时**写进 HTTP 状态码与响应信封，因此是「双层」契约：

| 场景                              | 行为                                                                                                 |
|---------------------------------|----------------------------------------------------------------------------------------------------|
| HTTP 非 2xx（400/403/404/422/500） | `SfcRemoteErrorDecoder` 解析信封后抛 `SfcRemoteApiException`（带 `httpStatus`/`code`/`businessCode`/`msg`） |
| HTTP 2xx 但信封 `code != 200`      | `requireSuccess()` / `getDataOrThrow()` 抛同一异常                                                      |
| 无凭证或凭证失效                        | core 固定返回 **403 + 信封**；`SfcRemoteApiException#isForbidden()` 可直接判断                                 |
| 响应体不是 core 信封                   | 落到 feign 默认解码器（`FeignException`），不吞原始响应体                                                           |

---

## 注意

- **不要**给流式上传用的 `RestClient` 装配 `@LoadBalanced` 或任何 `ClientHttpRequestInterceptor`：带拦截器的
  `DefaultRestClient` 会改用整体缓冲请求体的实现（大文件上传 OOM）。寻址交给 `SfcLoadBalancedClientHttpRequestFactory`。