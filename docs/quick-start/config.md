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

## 附录1：application.yml默认内容
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

```