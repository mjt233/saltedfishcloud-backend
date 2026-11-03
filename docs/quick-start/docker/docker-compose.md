# Docker Compose 部署指南

使用项目根目录的 `docker-compose.yml` 一键启动完整环境（后端 + MySQL + Redis）。

## 前置条件

```bash
git clone https://github.com/mjt233/saltedfishcloud-backend.git
cd saltedfishcloud-backend
```

## 必做：先创建 `.env` 并配置两个密钥

`docker-compose.yml` 中的 `SFC_JWT_SECRET`（JWT 会话密钥，恰好 32 字符）与 `SFC_INTERNAL_KEY`
（服务间内部接口共享密钥，不少于 32 字符）是**必填项且不提供缺省值**：未配置时
`docker compose up` / `docker compose config` 会直接报错退出，不会以空密钥启动。

在项目根目录创建 `.env`：

```properties
APP_TAG=latest
# Maven 镜像（网络受限时使用，如阿里云）
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
# 数据库
MYSQL_ROOT_PASSWORD=your-password
MYSQL_DATABASE=xyy
# 必填：JWT 会话密钥，必须恰好 32 个字符（可用 `openssl rand -hex 16` 生成）
SFC_JWT_SECRET=0123456789abcdef0123456789abcdef
# 必填：服务间内部接口共享密钥，不少于 32 个字符（可用 `openssl rand -hex 16` 生成）
SFC_INTERNAL_KEY=0123456789abcdef0123456789abcdef
```

> 若部署微服务模式，独立网关 `sfc-gateway` 必须注入**完全相同**的 `SFC_JWT_SECRET`，否则
> `/api/ext/**` 会出现"登录正常但全量 403/7005"的密钥漂移症状。

## 快速启动

```bash
docker-compose up -d
```

启动后访问 http://localhost:8087 即可。

Docker Compose 会自动构建镜像、创建网络和存储卷，并按依赖顺序启动三个服务：

| 服务            | 镜像                     | 端口            |
|---------------|------------------------|---------------|
| `web-backend` | saltedfishcloud:latest | 8087 (暴露到宿主机) |
| `mysql`       | mysql:8.0              | 仅容器内部         |
| `redis`       | redis:alpine           | 仅容器内部         |

## 使用 .env 自定义配置

在项目根目录创建 `.env` 文件可覆盖默认配置（其中 `SFC_JWT_SECRET` 与 `SFC_INTERNAL_KEY` 为**必填项，
没有缺省值**，见上一节）。一个完整的示例：

```properties
# 镜像版本
APP_TAG=latest
# Maven 镜像（网络受限时使用，如阿里云）
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
# 数据库
MYSQL_ROOT_PASSWORD=your-password
MYSQL_DATABASE=xyy
# JWT 会话密钥（必填，恰好 32 字符；与网关 sfc-gateway 的 SFC_JWT_SECRET 必须一致）
SFC_JWT_SECRET=<32 字符随机值，例如 openssl rand -hex 16 的输出>
# 服务间内部接口共享密钥（必填，不少于 32 字符）
SFC_INTERNAL_KEY=<不少于 32 字符的随机值>
```

修改 `.env` 后重新构建并启动：

```bash
docker-compose build --no-cache
docker-compose up -d
```

## 常用管理命令

```bash
# 查看状态
docker-compose ps

# 查看日志
docker-compose logs -f web-backend

# 停止服务（保留数据）
docker-compose down

# 停止服务并清除所有数据
docker-compose down -v
```

## 微服务全量编排（docker-compose-microservice-full.yml）

仓库根目录另有 `docker-compose-microservice-full.yml`，用于一次性启动「Nacos + 网关 + core + AI 服务 + 离线下载服务 + MySQL/Redis」的微服务模式：

```bash
docker compose -f docker-compose-microservice-full.yml -p saltedfishcloud-backend up -d
```

网络拓扑：

| 网络 | 接入者 | 用途 |
|---|---|---|
| `nacos` | nacos、sfc-gateway、sfc-ai-service、sfc-download-service、aria2、sfc-core | 服务互通网络：注册中心 + 所有需要互相调用的服务 |
| `xyy-backend-net` | sfc-core、mysql、redis | 数据网络：仅 core 访问数据库/缓存，不承载服务发现 |

⚠️ **sfc-core 是唯一同时接入两个网络的服务**：其注册到 Nacos 的地址由 `ADVERTISE_ADDRESS=sfc-core`
（compose 服务名）显式指定，不能依赖自动探测——容器 IP 会随重建变化，且可能落在调用方不在的网络上，
网关会报 `ConnectTimeoutException ... /<容器IP>:8087`。**不要删除该环境变量**；若修改 core 的
`container_name`/服务名，需同步修改它。原理与排查见[服务发现与注册](../../develop/microservice/service-discovery.md)。

验证注册地址是否生效：

1. 打开 Nacos 控制台 `http://<主机>:8080` → 服务管理 → `SFC` 分组 → `sfc-core`，实例 IP 应显示为
   `sfc-core`（而非 172.x 的容器 IP）；
2. 网关容器内执行
   `docker compose exec sfc-gateway sh -c 'curl -sS -m 5 -o /dev/null -w "%{http_code}\n" http://sfc-core:8087/api/hello/feature'`
   应返回 `200`；
3. 浏览器访问 `http://<主机>:9090`，入口不再出现网关连接超时。

## 相关文档

- [Dockerfile 构建](dockerfile.md)
- [配置参数](../config.md)
- [Docker Compose 官方文档](https://docs.docker.com/compose/)
