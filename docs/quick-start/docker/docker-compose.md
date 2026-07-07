# Docker Compose 部署指南

使用项目根目录的 `docker-compose.yml` 一键启动完整环境（后端 + MySQL + Redis）。

## 前置条件

```bash
git clone https://github.com/mjt233/saltedfishcloud-backend.git
cd saltedfishcloud-backend
```

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

在项目根目录创建 `.env` 文件可覆盖默认配置。一个完整的示例：

```properties
# 镜像版本
APP_TAG=latest
# Maven 镜像（网络受限时使用，如阿里云）
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
# 数据库
MYSQL_ROOT_PASSWORD=your-password
MYSQL_DATABASE=xyy
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
