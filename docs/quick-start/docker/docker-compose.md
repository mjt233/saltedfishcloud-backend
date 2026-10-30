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

## 相关文档

- [Dockerfile 构建](dockerfile.md)
- [配置参数](../config.md)
- [Docker Compose 官方文档](https://docs.docker.com/compose/)
