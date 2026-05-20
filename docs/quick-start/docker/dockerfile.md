# Dockerfile 构建

该文档介绍如何使用项目根目录下的 `Dockerfile` 构建咸鱼云网盘镜像，并说明构建参数、运行方式与注意事项。

!!! info "前提条件"
    通过Docker构建和部署需要先拉取咸鱼云源代码
    > git clone https://github.com/mjt233/saltedfishcloud-backend.git


## 1. 适用场景

适用于以下场景：

- 需要在容器环境中统一构建咸鱼云网盘
- 不希望在宿主机单独安装 JDK 25 与 Maven
- 需要在构建阶段切换 Maven 仓库镜像地址

## 2. Dockerfile 构建说明

当前项目使用多阶段构建：

1. 构建阶段使用 `maven:3.9.11-amazoncorretto-25-al2023`
2. 运行阶段使用 `amazoncorretto:25-al2023`
3. 构建阶段会执行 `mvn -s /tmp/maven-settings.xml install package`
4. 最终镜像会复制 `release` 目录内容，并默认加载 `conf/config.yml`

这意味着即使宿主机没有安装 Maven，也可以直接通过 Docker 完成构建。

## 3. 构建参数

### 3.1 Maven 镜像地址

`Dockerfile` 支持通过构建参数 `MAVEN_MIRROR_URL` 指定 Maven 镜像地址。

默认值为 Maven 官方中央仓库：

```text
https://repo.maven.apache.org/maven2
```

如果未显式传入该参数，则构建阶段默认使用上述地址。

## 4. 构建镜像

请在项目根目录执行以下命令。

### 4.1 使用默认 Maven 中央仓库构建

```shell
docker build -t saltedfishcloud:latest .
```

### 4.2 使用自定义 Maven 镜像构建

例如使用内网 Nexus 聚合仓库：

```shell
docker build \
    --build-arg MAVEN_MIRROR_URL=http://your-local-host:8081/repository/maven-public/ \
    -t saltedfishcloud:latest \
    .
```

构建时，`Dockerfile` 会动态生成 Maven `settings.xml`，并将所有仓库请求通过该镜像地址转发。

## 5. 运行镜像

构建完成后，可使用以下命令启动容器：

```shell
docker run -d \
    --name saltedfishcloud \
    -p 8087:8087 \
    -v /path/to/store:/saltedfish/store \
    -v /path/to/config.yml:/saltedfish/config.yml \
    saltedfishcloud:latest
```

## 6. 运行时约定

当前 `Dockerfile` 中的运行约定如下：

- 工作目录：`/saltedfish`
- 服务端口：`8087`
- 数据卷目录：`/saltedfish/store`
- 默认配置文件：`/saltedfish/config.yml`
- 启动命令：`java -jar sfc-core.jar --spring.config.import=file:config.yml`

如果需要覆盖默认配置，请将宿主机上的配置文件挂载到容器中的 `/saltedfish/config.yml`。

## 7. 相关文档

- [项目构建](../../develop/build.md)
- [Docker Compose 部署](docker-compose.md)
- [运行部署](../index.md)
