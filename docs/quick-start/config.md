# 参数配置

主要在config.yml中进行配置，也可以使用命令行进行配置。

## 配置示例

大部分情况下，推荐使用修改config.yml的方式进行配置，下面将以修改服务端口为例展示两种初始配置方式


### config.yml方式
```yaml
server:
  port: 8087
```

### 命令行方式
```yaml
java -jar sfc-core.jar --spring.config.import=file:config.yml --server.port=8087
```

### 以不依赖Redis和MySQL的方式运行

参考以下配置节点改为，停用redis，启用sqlite，启用本地缓存和消息队列:

!!! info "注意"

    需要启用`sfc-ext-local-mq`插件，`sys.service.mq-provider`才能使用`local`

```yaml
spring:
  datasource:
    driver-class-name: org.sqlite.JDBC
    url: "jdbc:sqlite:./xyy.db"

sys:
  redis:
    # Redis 是否启用，默认为 true。设置为 false 可以在不启动 Redis 的情况下运行系统
    # 注意：禁用Redis会影响缓存（cache-provider需设置为local）、资源锁（lock-provider需设置为local）、消息队列（mq-provider设置为local）、RPC等功能
    enabled: false
  service:
    # 缓存provider，默认 redis，可选 local（程序内缓存）
    cache-provider: local
    # 资源锁 provider，默认 redisson，可选 local（仅当前服务实例内生效，且不注入 Redisson）
    lock-provider: local
    # MQ provider，默认 redis
    # 启用 sfc-ext-local-mq 插件时，可选local。使用local仅支持单 JVM 进程内消息语义
    mq-provider: local
    # RPC provider，默认 redis，可选 mq（基于 MQService 实现）
    rpc-provider: mq
    # 服务注册 provider，默认 local（进程内注册表，未启用微服务模式）。可选 nacos（需 sfc-ext-nacos 插件）
    registry-provider: local
    # 注册到注册中心的自身服务名
    service-name: sfc-core
    # 注册中心分组，对应 Nacos 的 group。跨服务的互相可见依赖"所有服务注册到同一分组"这一部署约定
    registry-group: SFC
    # 本机对外可达地址（host 或 IP，不含端口）。多网卡 / 容器 / NAS 环境下自动解析出的地址可能不可达，此时显式指定；留空表示自动解析
    advertise-address: ""
    # Nacos provider 专属配置，仅在 registry-provider 为 nacos 时生效（由 sfc-ext-nacos 插件绑定）
    nacos:
      # 服务端地址，多节点用逗号分隔
      server-addr: 127.0.0.1:8848
      # 命名空间 ID。留空表示 public 命名空间（它的 ID 是空串，不要写 public）
      namespace: ""
      # 鉴权用户名与密码，服务端未开启鉴权时留空
      username: ""
      password: ""
      # 集群名，用于同一服务内实例的再分组
      cluster-name: DEFAULT
    # 控制面代理（阶段 8）：登录用户经【独立网关 sfc-gateway】调用微服务接口，路径形态 /api/ext/{serviceName}/**
    # ⚠️ core 不参与代理链路：凭证验签（密钥经环境变量 SFC_JWT_SECRET 注入）、路径校验、服务名白名单、
    #    api 前缀解析与转发全部在网关进程内（网关默认监听 8081，是整站唯一入口）。
    #    此处曾有的 sys.service.gateway.*（enabled / default-api-prefix）与 POST /api/gateway/resolve
    #    已随"网关独立验签"一并删除，不要再补回来。
```

完整配置见 [附录1: config.yml](#1configyml)

## 附录1：config.yml默认内容
```yaml
# 用户自定义程序配置文件

server:
  port: 8087
spring:
  jpa:
    database: mysql
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: "jdbc:mysql://${DB_HOST:127.0.0.1}/${DB_NAME:xyy}?${JDBC_PARAMETER:-useSSL=false&serverTimezone=UTC}"
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:test}
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      database: ${REDIS_DB:0}
#    password:

sys:
  redis:
    # Redis 是否启用，默认为 true。设置为 false 可以在不启动 Redis 的情况下运行系统
    # 注意：禁用Redis会影响缓存（cache-provider需设置为local）、资源锁（lock-provider需设置为local）、消息队列（mq-provider需设置为local）、RPC等功能
    enabled: ${REDIS_ENABLED:true}
  service:
    # 缓存provider，默认 redis，可选 local（程序内缓存）
    cache-provider: ${CACHE_PROVIDER:redis}
    # 资源锁 provider，默认 redisson，可选 local（仅当前服务实例内生效，且不注入 Redisson）
    lock-provider: ${LOCK_PROVIDER:redisson}
    # MQ provider，默认 redis
    # 启用 sfc-ext-local-mq 插件时，可选local。使用local仅支持单 JVM 进程内消息语义
    mq-provider: ${MQ_PROVIDER:redis}
    # RPC provider，默认 redis，可选 mq（基于 MQService 实现）
    rpc-provider: ${RPC_PROVIDER:redis}
    # 服务注册 provider，默认 local（进程内注册表，未启用微服务模式）。可选 nacos（需 sfc-ext-nacos 插件）
    registry-provider: ${REGISTRY_PROVIDER:local}
    # 注册到注册中心的自身服务名
    service-name: ${SERVICE_NAME:sfc-core}
    # 注册中心分组，对应 Nacos 的 group。跨服务的互相可见依赖"所有服务注册到同一分组"这一部署约定
    registry-group: ${REGISTRY_GROUP:SFC}
    # 本机对外可达地址（host 或 IP，不含端口）。多网卡 / 容器 / NAS 环境下自动解析出的地址可能不可达，此时显式指定；留空表示自动解析
    advertise-address: ${ADVERTISE_ADDRESS:}
    # Nacos provider 专属配置，仅在 registry-provider 为 nacos 时生效（由 sfc-ext-nacos 插件绑定）
    nacos:
      # 服务端地址，多节点用逗号分隔
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
      # 命名空间 ID。留空表示 public 命名空间（它的 ID 是空串，不要写 public）
      namespace: ${NACOS_NAMESPACE:}
      # 鉴权用户名与密码，服务端未开启鉴权时留空
      username: ${NACOS_USERNAME:}
      password: ${NACOS_PASSWORD:}
      # 集群名，用于同一服务内实例的再分组
      cluster-name: ${NACOS_CLUSTER_NAME:DEFAULT}
    # 控制面代理（阶段 8）：登录用户经【独立网关 sfc-gateway】调用微服务接口，路径形态 /api/ext/{serviceName}/**
    # ⚠️ core 不参与代理链路：凭证验签、路径校验、服务名白名单、api 前缀解析与转发全部在网关进程内；
    #    代理侧配置（core 服务名、监听端口、缺省 api 前缀、密钥环境变量名）在
    #    microservices/sfc-gateway 工程自己的 application.yml 内，不占用 sys. 前缀。
    #    注：网关【不】管控请求体大小（无上限配置项），请求体多大由承载业务的服务自己决定。
    # ⚠️ 框架期采用【信任模式】：网关本地验签得到 uid 后注入 X-Sfc-Uid，微服务直接信任；
    #    因此微服务与 core 的内部接口只允许内网可达，上公网前必须先完成服务间鉴权（阶段 5）。
    #    已落地的一层兜底：/api/internal/** 只允许微服务间调用——各服务用同一份共享密钥
    #    （sfc.internal.key ← 环境变量 SFC_INTERNAL_KEY，由 sfc-auth SDK 提供）为每次调用自签 HS256 JWT，
    #    core 侧校验「签名 + 有效期（含时钟偏移）+ 必需声明」，网关侧直接拒绝该前缀。
    #    该兜底不区分服务身份、不做防重放，仍不能替代阶段 5。
    #    此处曾有的 sys.service.gateway.* 与 POST /api/gateway/resolve 已整体删除，不要再补回来。
    local-cache:
      # 本地缓存默认过期时间（毫秒），默认 15 分钟
      default-expire-ms: ${LOCAL_CACHE_DEFAULT_EXPIRE_MS:900000}
      # 本地缓存最大数量，超过后按最旧写入顺序淘汰
      max-cache-size: ${LOCAL_CACHE_MAX_CACHE_SIZE:4096}
      # 是否启用本地缓存持久化，启用后会定时异步刷盘，并在系统关闭时再次落盘
      persist-enabled: ${LOCAL_CACHE_PERSIST_ENABLED:true}
      # 本地缓存持久化文件路径，系统启动时会尝试从该文件恢复缓存内容
      persist-file-path: ${LOCAL_CACHE_PERSIST_FILE_PATH:./localcache.data}
    local-mq:
      # 本地消息队列的历史消息记录最长长度，默认为 4096。过大的值可能会导致OOM
      max-queue-size: 4096
      # 淘汰目标比例，消息数超出 max-queue-size 时淘汰至 max-queue-size * evict-target-ratio，默认 0.85
      evict-target-ratio: 0.85
  store:
    # 压缩文件操作使用的文件编码格式，默认使用系统默认编码，Linux: UTF-8，Windows: GBK
    archive-encoding: ${ARCHIVE_ENCODING:}

    # 存储服务类型，可选hdfs（需要hdfs拓展）或local
    type: ${STORE_TYPE:local}

    # 除公共网盘外，各类资源根目录存储路径（缩略图、缓存、临时文件、私人网盘数据、用户头像、按哈希组织的文件集合等数据）
    root: ${STORE_ROOT:store}

    # 单独的公共网盘根目录路径
    # 仅在 管理员后台-系统-基础配置-存储模式 设置为 UNIQUE(默认) 时有效。（注：切换存储模式会触发全网盘数据重新组织，中途出错可能会导致数据丢失和损坏，请谨慎切换并做好数据备份）
    public-root: ${PUBLIC_ROOT:public}

    # minio存储配置
    # 安装了 sfc-ext-minio-store 插件后，sys.store.type设置为minio后生效
    minio:
      access-key: ${MINIO_ACCESS_KEY:xyy}
      secret-key: ${MINIO_SECRET_KEY:xyy123456}
      bucket: ${MINIO_BUCKET:xyy}

    # hdfs存储配置
    # 安装了 sfc-ext-hadoop-store 插件后，sys.store.type设置为hdfs后生效
    # 注意：该插件未在jdk25中进行测试，使用时请确保hadoop版本和jdk版本兼容，且hadoop环境配置正确，否则可能会导致程序无法启动或运行时异常
    hdfs:
      url: ${HDFS_URL:hdfs://localhost:9000}
      root: ${HDFS_ROOT:/xyy2}
      user: ${HDFS_USER:xiaotao}

```

## 附录2：application.yml默认内容
```yaml
server:
  port: 8087
  servlet:
    encoding:
      charset: utf-8
      enabled: true
      force: false
  tomcat:
    remoteip:
      remote-ip-header: X-Real-IP

spring:
  jpa:
    database: mysql
    open-in-view: false
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        order_inserts: true
        enable_lazy_load_no_trans: true
        jdbc:
          batch_size: 200

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1/d_xyy?useSSL=false&serverTimezone=UTC
    username: root
    password: test
    hikari:
      transaction-isolation: TRANSACTION_READ_COMMITTED
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      lettuce:
        pool:
          min-idle: 0
          max-idle: 8
          max-active: 8
  servlet:
    multipart:
      max-file-size: 8192MB
      max-request-size: 8192MB
  profiles:
    active: ^spring-profile^
  web:
    resources:
      add-mappings: false
logging:
  file:
    name: ./log/output.log
  level:
    org: warn
    com: warn
    com.sfc: info
    com.xiaotao: info
    com.saltedfishcloud: info
#    com.xiaotao.saltedfishcloud.SaltedfishcloudApplication: warn

management:
  health:
    mail:
      enabled: false
app:
  version: ^project.version^

# 内部接口（服务间调用）鉴权 SDK（sfc-auth）配置：/api/internal/** 只允许微服务间调用。
# 该节点不在 sys. 下（配置项前缀由 SDK 固定为 sfc.internal），但同样需要在各部署配置中同步。
# 密钥必须与所有微服务侧的 SFC_INTERNAL_KEY 完全一致。
sfc:
  internal:
    # 服务间共享密钥（环境变量 SFC_INTERNAL_KEY）。各项含义与默认值见 sfc-auth/README.md：
    # key / service-name / token-ttl / clock-skew / server-enabled / allowed-issuers
    key: ${SFC_INTERNAL_KEY:}
    server-enabled: true
```