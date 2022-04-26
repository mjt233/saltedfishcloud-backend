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

## 持久化参数

系统为了支持运行时调整参数，sys节点下的绝大部分参数都是仅限启动时生效，系统一旦完成首次启动初始化后，这些参数都将被数据库所存储，若需要调整则需要到管理员后台界面进行设置

以下配置节点不会被持久化：
- sys.sync-on-launch
- sys.store.*

后续将会开发参数统一配置功能

## 附录1：config.yml默认内容
```yaml
# 用户自定义程序配置文件

server:
  port: 8087
spring:
  jpa:
    database: mysql
  datasource:
    druid:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://127.0.0.1/d_xyy?useSSL=false&serverTimezone=UTC
      username: root
      password: test
  redis:
    host: 127.0.0.1
    port: 6379
#    database: 0
#    password:

sys:
  common:
    # 注册邀请码
    reg-code: 114514
  sync:
    # 自动同步间隔，单位分钟，-1表示关闭自动同步(该功能不稳定,不推荐使用同步和存储切换功能,建议一开始就确定好存储模式后不要再在管理员后台改动了)
    interval: -1

    # 咸鱼云启动时立即同步，默认关闭
    sync-on-launch: false
  store:
    # 压缩文件操作使用的文件编码格式，默认使用系统默认编码，Linux: UTF-8，Windows: GBK
    archive-encoding: gbk

    # 存储服务类型，可选hdfs（需要hdfs拓展）或local
    type: local

    # 各类资源根目录存储路径
    root: store

    # 公共网盘根目录路径
    public-root: public

    # 存储模式，可选 raw - 原始存储 或 unique - 唯一存储
    # 注意:仅首次启动有效, 后续需要切换请在管理员后台切换, 但需要注意的是切换功能不是特别稳定, 容易导致数据丢失, 所以最好一开始就确定好存储模式不要再动了
    # (切换功能已经修过好几次严重bug了, 虽然目前没发现有bug, 但不确保真的没bug)
    mode: unique
  ftp:
    # FTP控制端口
    control-port: 21

    # FTP监听地址
    listen-addr: 0.0.0.0

    # 是否开启FTP
    ftp-enable: true

    # FTP被动模式传输重定向地址
    passive-addr: localhost

    # FTP被动模式传输端口范围
    passive-port: 20000-30000

```

## 附录1：application.yml默认内容
```yaml
server:
  port: 8087
  servlet:
    encoding:
      charset: utf-8
      enabled: true
      force: true
  tomcat:
    remoteip:
      remote-ip-header: X-Real-IP
mybatis:
  configuration:
    map-underscore-to-camel-case: true
spring:
  jpa:
    database: mysql
    open-in-view: false
    hibernate:
      ddl-auto: none
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://127.0.0.1/d_xyy?useSSL=false&serverTimezone=UTC
      username: root
      password: test
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
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
logging:
  level:
    org: warn
    com: warn
    com.xiaotao: debug
    com.xiaotao.saltedfishcloud.dao.mybatis: warn
    com.xiaotao.saltedfishcloud.SaltedfishcloudApplication: warn
app:
  version: ^project.version^


sys:
  common:
    # 注册邀请码
    reg-code: 114514
  sync:
    # 自动同步间隔，单位分钟，-1表示关闭自动同步
    interval: -1

    # 咸鱼云启动时立即同步，默认关闭
    sync-on-launch: false
  store:
    # 压缩文件操作使用的文件编码格式，默认使用系统默认编码，Linux: UTF-8，Windows: GBK
    archive-encoding: gbk

    # 存储服务类型，可选hdfs（需要hdfs拓展）或local
    type: local

    # 各类资源根目录存储路径
    root: store

    # 公共网盘根目录路径
    public-root: public

    # 存储模式，可选 raw - 原始存储 或 unique - 唯一存储
    mode: unique
  ftp:
    # FTP控制端口
    control-port: 21

    # FTP监听地址
    listen-addr: 0.0.0.0

    # 是否开启FTP
    ftp-enable: true

    # FTP被动模式传输重定向地址
    passive-addr: localhost

    # FTP被动模式传输端口范围
    passive-port: 20000-30000

```