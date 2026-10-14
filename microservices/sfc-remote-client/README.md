# sfc-remote-client —— 微服务回调 sfc-core 的远程客户端 SDK

> 用 **OpenFeign 声明式接口**把 core 的网盘文件操作变成微服务里的普通方法调用：
> **获取列表 / 下载 / 上传 / 重命名 / 复制 / 粘贴 / 删除**。
>
> 对接的是 core 的 **`/api/fileV2/**`**（`FileV2Controller`：扁平参数、无 OAuth、直接用用户权限）。
> 本模块是**库**（不是可运行进程）：不继承 root `pom.xml`、零宿主依赖、不产出可执行 jar。

---

## 一、能力与端点映射

| 操作 | 客户端方法 | core 端点 |
|---|---|---|
| 获取文件列表 | `list(uid, path)` → `SfcJsonResult<List<SfcFileInfo>>` | `GET /api/fileV2/list?uid=&path=` |
| 下载文件 | `download(uid, path)` → `Resource` | `GET /api/fileV2/download?uid=&path=` |
| 上传文件 | `uploadFile(file, uid, path, name, mtime)` / `uploadBytes(content, name, uid, path, mtime)` | `POST /api/fileV2/upload`（multipart）|
| 重命名文件 | `rename(uid, path, oldName, newName)` | `POST /api/fileV2/rename?…` |
| 复制文件 | `copy(uid, sourceUid, targetUid, sourcePath, targetPath, overwrite, names)` | `POST /api/fileV2/copy?…` + JSON 数组体 |
| 粘贴文件 | `paste(uid, sourceUid, targetUid, sourcePath, targetPath, overwrite, names)` | `POST /api/fileV2/paste?…` + JSON 数组体 |
| 删除文件 | `delete(uid, path, names)` | `POST /api/fileV2/delete?…` + JSON 数组体 |

契约细节：

| 项 | 说明 |
|---|---|
| 列表返回 | **合并扁平列表**：目录在前、文件在后，每项带 `type`（1 目录 / 2 文件），`size == -1` 也表示目录；用 `SfcFileInfo#isDir()` 判定 |
| 下载路径 | `path` 是**完整路径（含文件名）**，core 内部拆成「目录 + 文件名」 |
| 上传参数 | `uid`/`path` 必填；`name` 可选（缺省取上传文件名）；`mtime` 可选（缺省取当前时间）；**md5 由 core 写流时实时计算** |
| 复制 / 粘贴 | `names` 是 JSON 数组；`sourceUid`/`targetUid` 客户端总是显式发送（同用户时传同一个 uid）；**粘贴 = 移动**（对应前端的「剪切 + 粘贴」）|
| 删除 | 走 **POST**（不用 DELETE 带请求体，省掉对 `feign-hc5` 的依赖）|
| 权限 | 写操作要求 `uid` 为本人或管理员（公共 uid=0 写需管理员）；复制校验「源可读 + 目标可写」，粘贴两端都按写权限校验 |
| 返回值 | JSON 操作统一返回 `SfcJsonResult<T>`：`requireSuccess()` 断言成功、`getDataOrThrow()` 取数据 |

---

## 二、快速上手

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
    private final SfcCoreFileClient fileClient;

    /** 列出 uid=7 的 /docs 目录 */
    public List<SfcFileInfo> list() {
        return fileClient.list(7, "/docs").getDataOrThrow();
    }

    /** 上传（MultipartFile 形态；name 传 null 则用上传文件名，mtime 传 null 则用当前时间） */
    public Long upload(MultipartFile file) {
        return fileClient.uploadFile(file, 7, "/docs", null, null).getDataOrThrow();
    }

    /** 上传（字节形态，必须给文件名） */
    public Long uploadBytes(byte[] content) {
        return fileClient.uploadBytes(content, "hello.txt", 7, "/docs", null).getDataOrThrow();
    }

    /** 复制到同用户目录；跨用户时把两个 uid 传成不同值 */
    public void copy() {
        fileClient.copy(7, 7, 7, "/src", "/dst", true, List.of("a.txt", "b.txt")).requireSuccess();
    }

    /** 粘贴（移动）：从公共网盘剪切到自己网盘 */
    public void paste() {
        fileClient.paste(7, 0, 7, "/pub", "/mine", false, List.of("p.txt")).requireSuccess();
    }

    /** 重命名与删除 */
    public void others() {
        fileClient.rename(7, "/docs", "old.txt", "new.txt").requireSuccess();
        fileClient.delete(7, "/docs", List.of("x.txt")).requireSuccess();
    }

    /** 下载（流式 Resource，调用方负责关闭流） */
    public byte[] download() throws IOException {
        try (InputStream in = fileClient.download(7, "/docs/a.txt").getInputStream()) {
            return in.readAllBytes();
        }
    }
}
```

---

## 三、配置项

| 配置键 | 默认值 | 说明 |
|---|---|---|
| `sfc.remote.client.enabled` | `true` | 关掉后本 SDK 不注册任何 bean，消费方自行 `@EnableFeignClients` |
| `sfc.remote.client.core-url` | 空 | core 直连地址（如 `http://127.0.0.1:8087`）。**留空走服务发现**：按服务名 `sfc-core` 从 Nacos 取实例并用 LoadBalancer 选路 |

超时、重试、日志级别沿用 Spring Cloud OpenFeign 标准配置，例如
`spring.cloud.openfeign.client.config.sfc-core.read-timeout=60000`。

> ⚠️ 服务发现寻址要求消费方注册到**同一个** Nacos（`group=SFC`、`namespace=public`）。

---

## 四、身份与凭证

| 项 | 做法 |
|---|---|
| 身份来源 | 网关本地验签后注入 `X-Sfc-Uid`，并把用户凭证原值透传给微服务 |
| 本 SDK | `CredentialForwardingInterceptor` 把当前入站请求的 `Token`（请求头 → 查询参数 → `token` Cookie）与 `X-Sfc-Uid` 原样带到回调请求上；**不产生、不解析、不校验**任何凭证 |
| 不打印 | 凭证是敏感数据：日志只记录「是否携带」，不记录其值 |
| 无 HTTP 上下文 | 定时任务/消息消费等线程**不伪造身份**，回调以匿名发起，需要登录的操作会被 core 拒绝（403）|

> 🔒 框架期为「信任模式」：微服务与内部接口**只允许内网/本机暴露**，对外入口只有 `sfc-gateway`。

---

## 五、错误语义

core 的业务错误**同时**写进 HTTP 状态码与响应信封，因此是「双层」契约：

| 场景 | 行为 |
|---|---|
| HTTP 非 2xx（400/403/404/422/500）| `SfcRemoteErrorDecoder` 解析信封后抛 `SfcRemoteApiException`（带 `httpStatus`/`code`/`businessCode`/`msg`）|
| HTTP 2xx 但信封 `code != 200` | `requireSuccess()` / `getDataOrThrow()` 抛同一异常 |
| 无凭证或凭证失效 | core 固定返回 **403 + 信封**；`SfcRemoteApiException#isForbidden()` 可直接判断 |
| 响应体不是 core 信封 | 落到 feign 默认解码器（`FeignException`），不吞原始响应体 |

---

## 六、模块构成

**契约层**（`sfc-common`，包 `com.sfc.cloud.remote`）：
`SfcJsonResult`（响应信封）、`SfcFileInfo`（文件信息）、`SfcRemoteApiException`（失败异常）、`SfcRemoteConstants`（服务名与凭证常量）。

**客户端**（本模块，包 `com.sfc.cloud.remote.client`）：
`SfcCoreFileClient`（唯一 Feign 接口）、`CredentialForwardingInterceptor`、`SfcRemoteErrorDecoder`、`SfcRemoteClientAutoConfiguration`。

**依赖**：`sfc-common` + `spring-cloud-starter-openfeign` + `spring-cloud-starter-loadbalancer` + `jakarta.servlet-api`(provided)。
**不需要** `feign-hc5`（无 DELETE/PUT 带请求体的接口）；无属性类、无 `application.yml`（避免与消费方配置互相覆盖）。

---

## 七、验收记录（2026-10-13）

| 项 | 结果 |
|---|---|
| `sfc-core` 编译（`mvn -pl sfc-core -am -DskipTests compile`）| BUILD SUCCESS，`FileV2Controller` 无告警 |
| 微服务全量构建（`mvn -f microservices/pom.xml clean package -DskipTests`）| 5 个模块 BUILD SUCCESS |
| 探针 A（客户端 → 本地桩服务，一次性、不入库）| **25/25 全绿**：查询参数形状、JSON 数组体、multipart 部件、`null` 的 `name`/`mtime` **不发送且不写成字面量 null**、`Resource` 下载解码、凭证透传（含 Cookie 兜底与「无上下文不伪造」）、错误信封 → 异常 |
| 探针 B（core 侧 standalone MockMvc + 假文件系统，一次性、不入库）| **25/25 全绿**：扁平列表合并与顺序、完整路径拆分为目录+文件名、上传 `uid`/`name`/`mtime` 绑定与文件内容/大小、重命名/复制/粘贴/删除参数绑定、空文件名列表 400、跨用户越权 403、注解静态检查（`@ProtectBlock`/`@AllowAnonymous`/`@NotBlock`/`@UID`/`@ValidPath`/`@FileName`，且上传 `mtime` 无 `@NotNull`）|
| 零宿主依赖 | `dependency:tree` 除 `com.sfc.cloud:sfc-common` 外无任何仓库内坐标 |

---

## 八、限制与后续

- 下载返回 `Resource`，但客户端侧仍会**整体读入内存**；真正的流式通道属路线图阶段 6.4。
- 需要 servlet 栈（凭证透传依赖 `HttpServletRequest`）。
- 前端（另一仓库）目前仍走 v1 接口；V2 是**新增**接口，`FileController` / `ResourceController` / `OpenApiDiskFileController` 均保留可用。
- 上传不接收 `md5`（core 写流时实时计算），因此没有「秒传」优化。
