# 起步

## 1. 打包与编译
对根项目执行maven的package，执行成功后会在release目录下创建程序主程序jar包和相关文件
创建release后，目录结构如下：
```
release
    ├── README.md                           - 自述文件
    ├── config.yml                          - 系统程序配置文件
    ├── ext                                 - 该目录下的jar包将被加载到系统
    ├── ext-available                       - 存放可用的拓展，拓展项目打包后会将其jar包复制到该处
    │       ├── sfc-ext-demo.jar            - 拓展演示demo
    │       └── sfc-ext-mp3-thumbnail.jar   - 让系统获得对mp3专辑缩略图提取支持
    ├── sfc-core.jar                        - 咸鱼云网盘主程序核心
    ├── start.bat                           - Windows用的bat启动脚本
    └── start.sh                            - Linux/Mac 下 bash/dash 用的启动脚本

```
### 1. 启动命令行指定外部spring yml配置文件

将主程序jar包同目录下的config.yml配置完成后，直接执行下面的命令即可启动。
当然你也可以修改file:config.yml这个参数指定任意路径的yml配置文件。

```shell
# 以下为start.sh的内容，是最简单的启动命令
java -jar sfc-core.jar --spring.config.import=file:config.yml
```

> 项目首次启动后会自动初始化数据库

---

!!! info


    配置文件`config.yml`的配置内容与spring的`application.yml`完全一致，且会覆盖默认的`application.yml`·`的内容。

    `config.yml`中未配置的项将取`application.yml`的配置项作为默认值

    具体配置项说明见[参数配置](./config.md)


## 3. 完成

至此，启动程序后便完成了后端服务器的部署，此时通过浏览器URL访问，可以看到

**咸鱼云网盘后端正在运行...**

这个页面，出现该页面说明程序运行成功😆

> 若已把前端产物打包进来（设置环境变量 `SFC_FRONT_END_PATH` 后执行 `mvn package -Pdevelop`，会把前端 `dist/**` 复制进 `sfc-core` 的 `webapp/`），浏览器看到的就是完整前端界面，而不是上面这句占位文案。

---

## 4. 微服务模式（可选）：再加一个独立网关

咸鱼云的微服务改造引入了**独立进程的网关** `sfc-gateway`（`microservices/sfc-gateway`，Spring Cloud Gateway）作为**整站唯一入口**：浏览器只与它同源对话，由它把 SPA、`/api/**`、WebSocket 与 OIDC 透传到 `sfc-core`，并把 `/api/ext/**` 代理到各个微服务。**用户凭证由网关自己验签**（密钥经环境变量注入），core 不参与代理链路。

```powershell
# 前置：Nacos 已启动，且 sfc-core 与目标微服务已注册（group=SFC / namespace=public）
# ⚠️ 必须先把 core 的会话密钥注入环境变量，否则网关启动即失败（fail-fast）
#    取法：管理端【系统设置 → 常规 → 系统安全密钥】，或 GET /api/admin/sys/config/config/sys.safe.token
$env:SFC_JWT_SECRET = '<core 的 sys.safe.token 值>'
java -Xmx512m -jar microservices/sfc-gateway/target/sfc-gateway-1.0.0.jar
# 浏览器访问 http://127.0.0.1:8081/（网关），而不是直连 core 的 8087
```

| 要点 | 说明 |
|---|---|
| 端口 | 网关默认 **8081**，core 仍是 8087（网关在本机时会避让 Nacos 控制台的 8080） |
| 构建命令 | **独立构建**：`mvn -f microservices/pom.xml clean package -DskipTests`（聚合器一次构建共享库 `sfc-common` 与网关；只构建网关用 `-pl :sfc-gateway -am`）。本目录的工程都不继承 root pom。⚠️ 它当前也在 root `<modules>` 中，因此根目录 `mvn package` 会一并构建；但它的产物**不打进 `release/` 目录**，是独立发布的进程 |
| ⚠️ 密钥（必做） | 网关用**环境变量 `SFC_JWT_SECRET`** 验签，取值必须与 core 的 `sys.safe.token` **一致**：漂移的症状是"登录正常，但 `/api/ext/**` 全量 403/7005"；core 在线改密钥后**必须重启网关**并重新注入 |
| 回退路径 | 不启动网关时，直连 `http://127.0.0.1:8087/` 一切照旧（网关只是入口，不是必需品） |
| ⚠️ 部署约束 | 微服务与 core 的内部接口**只允许内网可达**：框架期采用**信任模式**（网关本地验签后注入明文头 `X-Sfc-Uid`，微服务不校验），且**凭证原值会透传给微服务**（微服务不得打印凭证）；**上公网前必须先完成服务间鉴权** |
| 配置 | 网关自己的配置在 `microservices/sfc-gateway/src/main/resources/application.yml`（不使用 `sys.` 前缀）；**core 侧没有任何代理配置**——`sys.service.gateway.*` 与 `POST /api/gateway/resolve` 已随"网关独立验签"一并删除（见[参数配置](./config.md)） |

> 注：如果你是通过release页面下载的已经打包编译好的程序，此时应该可以直接访问到完整的功能页面。

该服务仅能提供后端API服务，您还需要对前端页面程序进行部署。或者通过其他接入该API系统的客户端（虽然我没开发:p，API接口文档见"咸鱼云PostmanAPI文档"）

系统默认创建的管理员账号信息如下：


| 用户名    | 密码        |
|:-------|:----------|
| admin  | admin666  |

> **注意：完成系统部署后请务必修改管理员账号密码**

