# 参数配置

sfc-core 使用 Spring Boot 标准的「多文件 + 环境变量」覆盖机制：默认值集中打包在 jar 内，部署时只覆盖需要修改的项。

## 1. 配置加载顺序

优先级由低到高（后加载的覆盖先加载的，未配置的项自动取更低层的值）：

| 层级 | 文件 / 来源                                                        | 作用                                                                                                                   |
|----|----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| 1  | `sfc-core/src/main/config/application.yml`（打包进 jar）            | **唯一默认值来源**：基础设施与部署可调项，全部写成 `${ENV:默认值}` 占位                                                                          |
| 2  | `application-develop.yml`（同目录，打包进 jar）                         | 仅在 `--spring.profiles.active=develop` 时加载：本地开发差异（SQLite、本地缓存/锁、调试日志等）                                                |
| 3  | 仓库根 `./config-local.yml`                                       | 开发者个人覆盖，仅 develop 下由第 2 层通过 `spring.config.import: optional:file:./config-local.yml` 自动导入；该文件已加入 `.gitignore`，不存在时忽略 |
| 4  | 外部 `config.yml`（发布目录，`--spring.config.import=file:config.yml`） | 部署覆盖：按需取消注释模板中的项（未取消注释的项取内置默认值），模板见 `pre-release/config.yml`                                                                           |
| 5  | 环境变量 / 命令行参数                                                   | 优先级最高，适合容器、CI 与临时调试                                                                                                  |

!!! info "说明"

    - 发布包默认只加载第 1 层；需要本地开发配置时显式激活 develop profile（见下文「本地开发」）。
    - 第 3 层的相对路径 `./config-local.yml` 相对**启动工作目录**解析，因此本地开发时请以仓库根为工作目录启动。
    - 完整默认值以 `sfc-core/src/main/config/application.yml` 与对应 `@ConfigurationProperties` 类为准，本文只列常用项。

## 2. 配置方式

### 2.1 环境变量（推荐）

容器化 / CI 部署无需挂载配置文件，只靠环境变量即可启动：

```shell
export SFC_JWT_SECRET="$(openssl rand -hex 16)"   # 恰好 32 字符
export SFC_INTERNAL_KEY="$(openssl rand -hex 16)" # 不少于 32 字符
export DB_HOST=127.0.0.1
export REDIS_HOST=127.0.0.1
java -jar sfc-core.jar
```

### 2.2 编辑 release 中的 config.yml

发布目录（`release/`）中的 `config.yml` 是可选的部署覆盖文件。模板 `pre-release/config.yml` 以
**全部注释**的形式列出了 `application.yml` 的全部可配置项（含两个必填密钥），默认等价于
「不做任何覆盖」：未取消注释的项一律取内置默认值。需要修改某项时，删除该项**及其层级父行**行首的
`# ` 即可（说明性注释可以保留）：

```yaml
# 模板节选（默认全部注释；删除行首的 "# " 即生效）
# server:
#   port: "${SERVER_PORT:8087}"
# sys:
#   jwt-secret: "${SFC_JWT_SECRET:}"
# sfc:
#   internal:
#     key: "${SFC_INTERNAL_KEY:}"
```

> 必填密钥 `sys.jwt-secret` / `sfc.internal.key` 推荐直接用环境变量 `SFC_JWT_SECRET` /
> `SFC_INTERNAL_KEY` 注入，也可以取消模板中的注释后填写。模板中的 `app.version` /
> `app.build-date` 由 Maven 打包时注入，请勿取消注释覆盖。

`release/start.sh` / `start.bat` 已包含 `--spring.config.import=file:config.yml`，因此改完 `config.yml`
后直接执行启动脚本即可。也可以把该参数改成任意路径的 yml 文件。

### 2.3 命令行参数

临时覆盖个别配置项（无需改文件）：

```shell
java -jar sfc-core.jar --spring.config.import=file:config.yml --server.port=8087
```

## 3. 常用配置组

> 下表只列常用项；完整默认值与注释见 `sfc-core/src/main/config/application.yml`，配置项含义以对应
`@ConfigurationProperties` 类为准。

### 3.1 服务器、数据库与 JPA

| 配置项                             | 默认值                                                          | 环境变量                                     | 说明                           |
|---------------------------------|--------------------------------------------------------------|------------------------------------------|------------------------------|
| `server.port`                   | `8087`                                                       | `SERVER_PORT`                            | HTTP 服务端口                    |
| `spring.application.name`       | `sfc-core`                                                   | `SERVICE_NAME`                           | 注册到注册中心的服务名                  |
| `spring.datasource.url`         | `jdbc:mysql://127.0.0.1/xyy?useSSL=false&serverTimezone=UTC` | `DB_HOST` / `DB_NAME` / `JDBC_PARAMETER` | 默认 MySQL；`DB_NAME` 默认 `xyy`  |
| `spring.datasource.username`    | `root`                                                       | `DB_USER`                                |                              |
| `spring.datasource.password`    | `test`                                                       | `DB_PASSWORD`                            | 生产环境务必修改                     |
| `spring.data.redis.host`        | `127.0.0.1`                                                  | `REDIS_HOST`                             |                              |
| `spring.data.redis.port`        | `6379`                                                       | `REDIS_PORT`                             |                              |
| `spring.data.redis.database`    | `0`                                                          | `REDIS_DB`                               |                              |
| `spring.jpa.database`           | `mysql`                                                      | —                                        | develop profile 下为 `default` |
| `spring.jpa.hibernate.ddl-auto` | `update`                                                     | —                                        | 由 JPA 自动创建 / 更新表结构           |

### 3.2 服务注册与发现（local / Nacos 二选一）

| 配置项                                                  | 默认值              | 环境变量                                | 说明                                         |
|------------------------------------------------------|------------------|-------------------------------------|--------------------------------------------|
| `spring.cloud.sfc-local.registry.enabled`            | `true`           | `SFC_LOCAL_ENABLED`                 | core 进程内 local 注册中心；启用 Nacos 时须设为 `false`  |
| `spring.cloud.sfc-local.registry.password`           | 空                | `SFC_LOCAL_REGISTRY_PASSWORD`       | 外部服务注册密码，留空表示禁用外部注册接口；develop 下默认 `114514` |
| `spring.cloud.sfc-local.registry.heartbeat-timeout`  | `30s`            | `LOCAL_REGISTRY_HEARTBEAT_TIMEOUT`  | 超过该时间未续约的实例会被移除                            |
| `spring.cloud.sfc-local.registry.cleanup-interval`   | `10s`            | `LOCAL_REGISTRY_CLEANUP_INTERVAL`   | 过期实例清理任务执行间隔                               |
| `spring.cloud.nacos.discovery.enabled`               | `false`          | `NACOS_ENABLED`                     | 启用 Nacos 时需同时设置 `SFC_LOCAL_ENABLED=false`  |
| `spring.cloud.nacos.discovery.server-addr`           | `127.0.0.1:8848` | `NACOS_SERVER_ADDR`                 | 多节点用逗号分隔                                   |
| `spring.cloud.nacos.discovery.namespace`             | 空                | `NACOS_NAMESPACE`                   | public 命名空间的 ID 是空串，不要写 `public`           |
| `spring.cloud.nacos.discovery.username` / `password` | 空                | `NACOS_USERNAME` / `NACOS_PASSWORD` | 服务端未开启鉴权时留空                                |
| `spring.cloud.nacos.discovery.group`                 | `SFC`            | `REGISTRY_GROUP`                    | 必须与各微服务一致                                  |
| `spring.cloud.nacos.discovery.cluster-name`          | `DEFAULT`        | `NACOS_CLUSTER_NAME`                | 同一服务内实例的再分组                                |
| `spring.cloud.nacos.discovery.ip`                    | 空（自动探测）          | `ADVERTISE_ADDRESS`                 | 本机对外可达地址；容器多网络部署必须显式指定                     |

### 3.3 系统配置（`sys.*`）

| 配置项                                                    | 默认值                                           | 环境变量                                                     | 说明                                             |
|--------------------------------------------------------|-----------------------------------------------|----------------------------------------------------------|------------------------------------------------|
| `sys.jwt-secret`                                       | 空                                             | `SFC_JWT_SECRET`                                         | **必填**，必须恰好 32 字符；缺失或长度不符时启动失败                 |
| `sys.redis.enabled`                                    | `true`                                        | `REDIS_ENABLED`                                          | 设为 `false` 可在不启动 Redis 的情况下运行，需同步调整下列 provider |
| `sys.oidc.enabled`                                     | `false`                                       | `OIDC_ENABLED`                                           | 内置 OIDC/OAuth2 授权服务器开关；端点路径使用代码默认值，无需配置        |
| `sys.service.cache-provider`                           | `redis`                                       | `CACHE_PROVIDER`                                         | 缓存实现，可选 `local`（进程内缓存）                         |
| `sys.service.lock-provider`                            | `redisson`                                    | `LOCK_PROVIDER`                                          | 资源锁实现，可选 `local`（仅当前实例内生效）                     |
| `sys.service.mq-provider`                              | `redis`                                       | `MQ_PROVIDER`                                            | 消息队列实现，可选 `local`（需启用 `sfc-ext-local-mq` 插件）   |
| `sys.service.rpc-provider`                             | `redis`                                       | `RPC_PROVIDER`                                           | RPC 实现，可选 `mq`（基于 MQService）                   |
| `sys.store.archive-encoding`                           | 空（系统默认编码）                                     | `ARCHIVE_ENCODING`                                       | 压缩文件操作使用的编码                                    |
| `sys.store.type`                                       | `local`                                       | `STORE_TYPE`                                             | 存储类型；`minio` / `hdfs` 需安装对应扩展插件                |
| `sys.store.root`                                       | `store`                                       | `STORE_ROOT`                                             | 私有网盘、缩略图、缓存、临时文件等根目录                           |
| `sys.store.public-root`                                | `public`                                      | `PUBLIC_ROOT`                                            | 公共网盘根目录（仅在 UNIQUE 存储模式下生效）                     |
| `sys.store.minio.access-key` / `secret-key` / `bucket` | `xyy` / `xyy123456` / `xyy`                   | `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET` | 安装 `sfc-ext-minio-store` 且 `type=minio` 时生效    |
| `sys.store.hdfs.url` / `root` / `user`                 | `hdfs://localhost:9000` / `/xyy2` / `xiaotao` | `HDFS_URL` / `HDFS_ROOT` / `HDFS_USER`                   | 安装 `sfc-ext-hadoop-store` 且 `type=hdfs` 时生效    |

### 3.4 内部接口与任务 Worker

| 配置项                                    | 默认值    | 环境变量                            | 说明                                          |
|----------------------------------------|--------|---------------------------------|---------------------------------------------|
| `sfc.internal.key`                     | 空      | `SFC_INTERNAL_KEY`              | **必填**（单机部署也必须配置），不少于 32 字符；core 与所有微服务必须一致 |
| `sfc.task.worker.role`                 | `CORE` | —                               | core 节点内嵌 Worker 的角色，固定为 `CORE`             |
| `sfc.task.worker.max-concurrent-tasks` | `64`   | `SFC_TASK_MAX_CONCURRENT_TASKS` | 单实例最大并发执行任务数                                |
| `sfc.task.worker.claim-wait-seconds`   | `10`   | —                               | 无任务可认领时的长轮询等待秒数                             |

## 4. 环境变量完整表

所有环境变量都可以直接覆盖对应配置项，无需修改任何 yml；默认值即 `application.yml` 中的取值。

| 分类         | 环境变量                               | 对应配置项                                               | 默认值                               |
|------------|------------------------------------|-----------------------------------------------------|-----------------------------------|
| 数据库        | `DB_HOST`                          | `spring.datasource.url`                             | `127.0.0.1`                       |
| 数据库        | `DB_NAME`                          | `spring.datasource.url`                             | `xyy`                             |
| 数据库        | `DB_USER`                          | `spring.datasource.username`                        | `root`                            |
| 数据库        | `DB_PASSWORD`                      | `spring.datasource.password`                        | `test`                            |
| 数据库        | `JDBC_PARAMETER`                   | `spring.datasource.url` 的查询参数                       | `useSSL=false&serverTimezone=UTC` |
| Redis      | `REDIS_HOST`                       | `spring.data.redis.host`                            | `127.0.0.1`                       |
| Redis      | `REDIS_PORT`                       | `spring.data.redis.port`                            | `6379`                            |
| Redis      | `REDIS_DB`                         | `spring.data.redis.database`                        | `0`                               |
| Redis      | `REDIS_ENABLED`                    | `sys.redis.enabled`                                 | `true`                            |
| 服务         | `SERVER_PORT`                      | `server.port`                                       | `8087`                            |
| 服务         | `SERVICE_NAME`                     | `spring.application.name`                           | `sfc-core`                        |
| local 注册中心 | `SFC_LOCAL_ENABLED`                | `spring.cloud.sfc-local.registry.enabled`           | `true`                            |
| local 注册中心 | `SFC_LOCAL_REGISTRY_PASSWORD`      | `spring.cloud.sfc-local.registry.password`          | 空（develop 下 `114514`）             |
| local 注册中心 | `LOCAL_REGISTRY_HEARTBEAT_TIMEOUT` | `spring.cloud.sfc-local.registry.heartbeat-timeout` | `30s`                             |
| local 注册中心 | `LOCAL_REGISTRY_CLEANUP_INTERVAL`  | `spring.cloud.sfc-local.registry.cleanup-interval`  | `10s`                             |
| Nacos      | `NACOS_ENABLED`                    | `spring.cloud.nacos.discovery.enabled`              | `false`                           |
| Nacos      | `NACOS_SERVER_ADDR`                | `spring.cloud.nacos.discovery.server-addr`          | `127.0.0.1:8848`                  |
| Nacos      | `NACOS_NAMESPACE`                  | `spring.cloud.nacos.discovery.namespace`            | 空                                 |
| Nacos      | `NACOS_USERNAME`                   | `spring.cloud.nacos.discovery.username`             | 空                                 |
| Nacos      | `NACOS_PASSWORD`                   | `spring.cloud.nacos.discovery.password`             | 空                                 |
| Nacos      | `REGISTRY_GROUP`                   | `spring.cloud.nacos.discovery.group`                | `SFC`                             |
| Nacos      | `NACOS_CLUSTER_NAME`               | `spring.cloud.nacos.discovery.cluster-name`         | `DEFAULT`                         |
| Nacos      | `ADVERTISE_ADDRESS`                | `spring.cloud.nacos.discovery.ip`                   | 空（自动探测）                           |
| 密钥         | `SFC_JWT_SECRET`                   | `sys.jwt-secret`                                    | 空（**必填**，恰好 32 字符）                |
| 密钥         | `SFC_INTERNAL_KEY`                 | `sfc.internal.key`                                  | 空（**必填**，不少于 32 字符）               |
| 服务实现       | `CACHE_PROVIDER`                   | `sys.service.cache-provider`                        | `redis`                           |
| 服务实现       | `LOCK_PROVIDER`                    | `sys.service.lock-provider`                         | `redisson`                        |
| 服务实现       | `MQ_PROVIDER`                      | `sys.service.mq-provider`                           | `redis`                           |
| 服务实现       | `RPC_PROVIDER`                     | `sys.service.rpc-provider`                          | `redis`                           |
| 存储         | `ARCHIVE_ENCODING`                 | `sys.store.archive-encoding`                        | 空（系统默认编码）                         |
| 存储         | `STORE_TYPE`                       | `sys.store.type`                                    | `local`                           |
| 存储         | `STORE_ROOT`                       | `sys.store.root`                                    | `store`                           |
| 存储         | `PUBLIC_ROOT`                      | `sys.store.public-root`                             | `public`                          |
| 存储         | `MINIO_ACCESS_KEY`                 | `sys.store.minio.access-key`                        | `xyy`                             |
| 存储         | `MINIO_SECRET_KEY`                 | `sys.store.minio.secret-key`                        | `xyy123456`                       |
| 存储         | `MINIO_BUCKET`                     | `sys.store.minio.bucket`                            | `xyy`                             |
| 存储         | `HDFS_URL`                         | `sys.store.hdfs.url`                                | `hdfs://localhost:9000`           |
| 存储         | `HDFS_ROOT`                        | `sys.store.hdfs.root`                               | `/xyy2`                           |
| 存储         | `HDFS_USER`                        | `sys.store.hdfs.user`                               | `xiaotao`                         |
| 任务         | `SFC_TASK_MAX_CONCURRENT_TASKS`    | `sfc.task.worker.max-concurrent-tasks`              | `64`                              |
| OIDC       | `OIDC_ENABLED`                     | `sys.oidc.enabled`                                  | `false`                           |

## 5. 本地开发

### 5.1 以 develop profile 启动

```shell
# 在仓库根目录启动
java -jar sfc-core/target/sfc-core.jar --spring.profiles.active=develop
# 或通过环境变量
SPRING_PROFILES_ACTIVE=develop java -jar sfc-core/target/sfc-core.jar
```

develop profile 相对内置默认值的差异（完整见 `sfc-core/src/main/config/application-develop.yml`）：

- 数据源改用 SQLite：`jdbc:sqlite:./xyy.db`（文件位于启动工作目录），`spring.jpa.database=default` 并打印 SQL；
- 模板缓存关闭，`com.sfc` / `com.xiaotao` / `com.saltedfishcloud` 等日志级别为 `debug`；
- 缓存与资源锁改用进程内实现（`cache-provider=local`、`lock-provider=local`），RPC 走 MQ（`rpc-provider=mq`）；
- 存储根目录使用默认值（`store` / `public`）；如需把开发数据与发布数据隔离，可在 `config-local.yml` 中覆盖为
  `store/dev/root` 与 `store/dev/public`（原 `application-develop-mysql.yml` 的默认值）；
- 内置开发用 JWT 默认值（可用 `SFC_JWT_SECRET` 覆盖），local 注册中心外部注册密码默认 `114514`；
- 自动导入仓库根 `./config-local.yml`（不存在时忽略）。

在 IDE 中运行 `SaltedfishcloudApplication` 时，把运行配置的 **Working directory** 设为仓库根，并添加启动参数
`--spring.profiles.active=develop`（或环境变量 `SPRING_PROFILES_ACTIVE=develop`）。

### 5.2 config-local.yml：个人本地覆盖

`config-local.yml` 位于仓库根，仅在 develop profile 下由 `application-develop.yml` 自动导入；该文件已在 `.gitignore` 中，*
*不要提交**（首次使用自行创建即可）。示例：

```yaml
# 仓库根 config-local.yml

# 1) 本地调试切 MySQL + Redis（不写则使用 SQLite + 进程内缓存/锁）
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3306/xyy?useSSL=false&serverTimezone=UTC
    username: root
    password: test
  data:
    redis:
      host: 127.0.0.1
      port: 6379
sys:
  service:
    cache-provider: redis
    lock-provider: redisson
    rpc-provider: redis

# 2) 开发时直接加载本地插件工程（路径相对仓库根）
plugin:
  extra-resource:
    - sfc-ext/sfc-ext-demo
```

> `./config-local.yml` 相对启动工作目录解析：只有以仓库根为工作目录启动时才会被自动导入。

### 5.3 不依赖 MySQL / Redis 运行

- 本地开发：直接使用 `--spring.profiles.active=develop`（内置 SQLite + 进程内缓存/锁），无需启动 MySQL 与 Redis。
- 正式部署不推荐关闭 Redis；如确需，设置 `REDIS_ENABLED=false`，并把 `CACHE_PROVIDER` / `LOCK_PROVIDER` 改为 `local`、
  `MQ_PROVIDER` 改为 `local`（需启用 `sfc-ext-local-mq` 插件）、`RPC_PROVIDER` 改为 `mq`。`pre-release/config.yml`
  的注释中有对应示例。
- 注意：MQ 默认仍为 `redis`；完全离线运行需启用 `sfc-ext-local-mq` 插件，并把 `mq-provider` 改为 `local`（`rpc-provider` 保持
  `mq`，即基于本地 MQ 实现）。

## 6. JWT 会话密钥（必须配置）

用户登录凭证与文件直链的签名/验签密钥来自配置项 `sys.jwt-secret`（环境变量 `SFC_JWT_SECRET`），
**不再保存在数据库配置表中，也不再首次启动时随机生成**。要求：

- 必须**恰好 32 个字符**（例如 `openssl rand -hex 16`）；
- 未配置或长度不符时 **core 启动失败（fail-fast）**，并在日志中提示 `SFC_JWT_SECRET`；
- 启用微服务模式时，独立网关 `sfc-gateway` 必须注入**完全相同**的 `SFC_JWT_SECRET`，
  否则 `/api/ext/**` 会出现"登录正常但全量 403/7005"的密钥漂移症状。

```yaml
sys:
  jwt-secret: ${SFC_JWT_SECRET:}
```

## 7. 内部接口密钥（单机部署也必须配置）

`/api/internal/**` 是服务间内部接口，调用凭证由 `sfc.internal.key`（环境变量 `SFC_INTERNAL_KEY`）签发与校验，
配置项不在 `sys.` 节点下。**即使是单机部署、未启用任何微服务，也必须配置该密钥**：core 节点内嵌的任务 Worker
通过内部 HTTP 调用本机的调度接口完成认领与上报，未配置时：

- 服务端拒绝全部 `/api/internal/**` 请求（fail-closed，启动日志打印 ERROR）；
- 内嵌 Worker 自检失败，启动日志打印 ERROR 并自动禁用认领（应用继续运行，但任务不会被 core 执行）。

要求：不少于 32 个字符（例如 `openssl rand -hex 16`）；多服务部署时 core 与所有微服务必须使用**完全相同**的密钥。

```yaml
sfc:
  internal:
    # 服务间共享密钥，环境变量 SFC_INTERNAL_KEY 注入
    key: ${SFC_INTERNAL_KEY:}
```

## 8. 附录

### 8.1 完整默认值与部署模板

- `application.yml` 完整默认值见 `sfc-core/src/main/config/application.yml`；
- `config.yml` 部署覆盖模板见 `pre-release/config.yml`（打包后复制为 `release/config.yml`）；
- 本地开发差异见 `sfc-core/src/main/config/application-develop.yml`；个人覆盖写在仓库根 `config-local.yml`（gitignore）。

### 8.2 关键项速查

| 场景       | 配置项 / 环境变量                                                                                                       | 取值                                       |
|----------|------------------------------------------------------------------------------------------------------------------|------------------------------------------|
| 改端口      | `SERVER_PORT`                                                                                                    | 默认 `8087`                                |
| 连数据库     | `DB_HOST` / `DB_NAME` / `DB_USER` / `DB_PASSWORD`                                                                | 默认 `127.0.0.1` / `xyy` / `root` / `test` |
| 连 Redis  | `REDIS_HOST` / `REDIS_PORT` / `REDIS_DB`                                                                         | 默认 `127.0.0.1` / `6379` / `0`            |
| 必填密钥 1   | `SFC_JWT_SECRET`（`sys.jwt-secret`）                                                                               | 恰好 32 字符                                 |
| 必填密钥 2   | `SFC_INTERNAL_KEY`（`sfc.internal.key`）                                                                           | 不少于 32 字符                                |
| 关闭 Redis | `REDIS_ENABLED=false` + `CACHE_PROVIDER=local` + `LOCK_PROVIDER=local` + `MQ_PROVIDER=local` + `RPC_PROVIDER=mq` | MQ 的 local 需 `sfc-ext-local-mq` 插件       |
| 存储目录     | `STORE_ROOT` / `PUBLIC_ROOT`                                                                                     | 默认 `store` / `public`                    |
| 本地开发     | `--spring.profiles.active=develop`                                                                               | SQLite + 进程内缓存/锁                         |
