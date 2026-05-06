# Docker 与容器化部署

该部分文档介绍如何使用 Docker 构建和部署咸鱼云网盘项目。

## 文档导览

### [Dockerfile 构建](dockerfile.md)

介绍如何使用项目根目录下的 `Dockerfile` 构建咸鱼云网盘镜像，包括：

- 多阶段构建说明
- 构建参数配置（Maven 镜像地址等）
- 镜像运行时约定

适用于需要自助构建镜像的场景。

### [Docker Compose 部署](docker-compose.md)

介绍如何使用 `docker-compose.yml` 一键启动完整的开发环境，包括：

- 服务配置说明
- 构建参数与环境变量详解
- 启动与停止操作
- 常见问题排查

适用于开发、测试和快速部署场景。

## 快速开始

### 使用 Docker Compose 启动（推荐）

```shell
docker-compose build
docker-compose up -d
```

### 使用 Dockerfile 自助构建

```shell
docker build -t saltedfishcloud:latest .
docker run -d \
    --name saltedfishcloud \
    -p 8087:8087 \
    -v /path/to/store:/saltedfish/store \
    saltedfishcloud:latest
```

## 相关链接

- [项目构建](../build.md)
- [运行部署](../../quick-start/index.md)
- [配置参数](../../quick-start/config.md)
