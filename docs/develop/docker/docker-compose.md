# Docker Compose 部署指南

该文档介绍如何使用项目根目录下的 `docker-compose.yml` 一键启动完整的开发环境，并详细说明构建参数与环境变量的使用。

## 1. 适用场景

适用于以下场景：

- 本地开发与测试：快速启动完整的开发环境（后端 + MySQL + Redis）
- 容器化部署：无需手动配置各个服务之间的网络隔离和依赖关系
- 跨平台开发：在 Linux、macOS、Windows 保持一致的运行环境
- CI/CD 集成：在持续集成流程中自动构建和启动服务

## 2. 服务架构

`docker-compose.yml` 定义了以下三个服务：

| 服务名称          | 镜像                     | 端口   | 说明                 |
|---------------|------------------------|------|--------------------|
| `web-backend` | saltedfishcloud:latest | 8087 | 咸鱼云后端服务            |
| `mysql`       | mysql:8.0              | 3306 | MySQL 数据库（仅容器内部访问） |
| `redis`       | redis:alpine           | 6379 | Redis 缓存（仅容器内部访问）  |

这些服务通过 `xyy-backend-net` 网络互相通信。

## 3. 构建参数

### 3.1 MAVEN_MIRROR_URL（Maven 镜像地址）

用于指定 Maven 依赖的远程仓库地址。

- **默认值**：`https://repo.maven.apache.org/maven2` （官方中央仓库）
- **环境变量**：`${MAVEN_MIRROR_URL}`
- **使用场景**：当网络环境无法访问官方仓库时，可配置内网或镜像源

#### 示例：使用阿里云 Maven 镜像

创建 `.env` 文件（项目根目录）：

```properties
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
```

然后启动：

```shell
docker-compose up -d
```

#### 示例：使用本地 Nexus 仓库

```bash
docker-compose build \
    --build-arg MAVEN_MIRROR_URL=http://your-nexus-host:8081/repository/maven-public/ \
    web-backend
docker-compose up -d
```

或直接在 `.env` 文件中指定：

```properties
MAVEN_MIRROR_URL=http://your-nexus-host:8081/repository/maven-public/
```

### 3.2 APP_TAG（应用镜像标签）

用于指定构建后的镜像标签。

- **默认值**：`latest`
- **环境变量**：`${APP_TAG}`
- **说明**：用于版本管理和多版本并行部署

#### 示例：指定版本标签

```properties
APP_TAG=v1.0.0
```

启动时将使用 `saltedfishcloud:v1.0.0` 镜像。

## 4. 环境变量详解

### 4.1 后端服务环境变量（`web-backend`）

| 变量名              | 默认值                                                            | 说明                               |
|------------------|----------------------------------------------------------------|----------------------------------|
| `DB_HOST`        | `mysql`                                                        | MySQL 数据库主机名（固定值，指向容器内 MySQL 服务） |
| `REDIS_HOST`     | `redis`                                                        | Redis 服务主机名（固定值，指向容器内 Redis 服务）  |
| `JDBC_PARAMETER` | `useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` | JDBC 连接参数                        |

这些环境变量会被传递到 Java 应用中，通常通过配置文件 `application-docker.yml` 或 Spring Boot 属性读取。

#### 自定义环境变量

如需修改这些变量，可在 `.env` 文件中添加或编辑（需重新构建或重启容器）：

```properties
# 数据库连接参数示例
DB_HOST=mysql
REDIS_HOST=redis
JDBC_PARAMETER=useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&maxPoolSize=20
```

### 4.2 MySQL 环境变量

| 变量名                   | 默认值    | 说明              |
|-----------------------|--------|-----------------|
| `MYSQL_ROOT_PASSWORD` | `test` | MySQL root 用户密码 |
| `MYSQL_DATABASE`      | `xyy`  | 初始数据库名称         |

**安全提示**：生产环境中务必修改 `MYSQL_ROOT_PASSWORD` 为强密码。

#### 示例：修改 MySQL 密码

在 `.env` 文件中添加：

```properties
MYSQL_ROOT_PASSWORD=your-secure-password-here
```

然后重新启动：

```bash
docker-compose down -v  # 删除旧的 MySQL 数据卷
docker-compose up -d
```

## 5. 使用 .env 文件管理配置

Docker Compose 会自动读取项目根目录的 `.env` 文件中的变量。创建 `.env` 文件可以管理所有配置：

```properties
# .env 文件示例
APP_TAG=latest
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
DB_HOST=mysql
REDIS_HOST=redis
MYSQL_ROOT_PASSWORD=secure_password_123
MYSQL_DATABASE=xyy
JDBC_PARAMETER=useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

然后直接执行：

```shell
docker-compose up -d
```

容器启动时会自动应用 `.env` 中的配置。

## 6. 快速启动指南

### 6.1 初次启动（自动构建）

```bash
# 在项目根目录执行
docker-compose up -d
```

Docker Compose 会自动：

1. 检查镜像是否存在，如不存在则调用 `docker build`
2. 创建网络和存储卷
3. 启动三个服务，并按依赖关系顺序启动（MySQL → Redis → 后端）

### 6.2 使用已有镜像启动（不重新构建）

```bash
docker-compose up -d --no-build
```

### 6.3 查看服务状态

```bash
docker-compose ps
```

输出示例：

```
NAME                     STATUS              PORTS
saltedfishcloud-web-backend-1    Up 2 minutes        0.0.0.0:8087->8087/tcp
saltedfishcloud-redis-1          Up 2 minutes        
saltedfishcloud-mysql-1          Up 2 minutes        
```

### 6.4 查看服务日志

查看所有服务日志：

```bash
docker-compose logs -f
```

查看特定服务日志：

```bash
docker-compose logs -f web-backend
docker-compose logs -f mysql
docker-compose logs -f redis
```

### 6.5 停止服务

```bash
# 停止所有服务（保留数据卷）
docker-compose down

# 停止所有服务并删除数据卷
docker-compose down -v
```

## 7. 存储卷与数据持久化

### 7.1 已配置的存储卷

| 卷名称              | 挂载点                 | 说明               |
|------------------|---------------------|------------------|
| `xyy-store-vol`  | `/saltedfish/store` | 应用数据存储目录（上传的文件等） |
| `xyy-mysql-data` | `/var/lib/mysql`    | MySQL 数据文件       |

这些卷会自动创建，数据会在容器重启后保留。

### 7.2 查看存储卷信息

```bash
docker volume ls

# 查看卷详细信息
docker volume inspect xyy-store-vol
```

### 7.3 清理旧数据

如需完全清除所有存储卷（谨慎操作）：

```bash
docker-compose down -v
```

## 8. 网络配置

### 8.1 服务间通信

- 内部网络：`xyy-backend-net`
- 后端可以通过 `mysql:3306` 和 `redis:6379` 访问对应服务
- 仅 `web-backend` 的 8087 端口暴露到宿主机

### 8.2 访问应用

启动后，可以通过以下地址访问应用：

```
http://localhost:8087
```

## 9. 构建优化

### 9.1 加速构建

#### 使用本地镜像缓存

多次构建时，Docker 会利用已有的镜像层缓存，加快构建速度。避免容易变化的操作（如 git clone）放在 Dockerfile 早期。

#### 配置 Maven 缓存

虽然 docker-compose 默认不挂载 Maven 缓存目录，但可以在 `docker-compose.yml` 中添加：

```yaml
volumes:
  - maven-cache:/root/.m2
```

然后在 `volumes:` 部分定义：

```yaml
volumes:
  maven-cache:
```

这样可以在多次构建间共享 Maven 本地仓库，显著加快构建速度。

### 9.2 只构建不启动

```bash
docker-compose build
```

### 9.3 重新构建（忽略缓存）

```bash
docker-compose build --no-cache
```

## 10. 常见问题排查

### 10.1 构建失败：无法下载 Maven 依赖

**症状**：构建时出现 `Connection refused` 或 `Download error`

**排查步骤**：

1. 检查 Maven 镜像地址是否可访问：
   ```bash
   curl https://maven.aliyun.com/repository/public/
   ```

2. 如果官方源无法访问，修改 `.env`：
   ```properties
   MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
   ```

3. 重新构建：
   ```bash
   docker-compose build --no-cache
   ```

### 10.2 端口冲突：8087 已被占用

**症状**：`docker-compose up` 时报错 `bind: address already in use`

**解决方法**：

方法一 - 修改宿主机端口（编辑 `docker-compose.yml`）：

```yaml
ports:
  - name: xyy-backend-port
    target: 8087
    published: 8888  # 改为未被占用的端口
```

方法二 - 使用新的 `.env` 配置重写端口（推荐）：

创建 `docker-compose.override.yml`：

```yaml
services:
  web-backend:
    ports:
      - "8888:8087"
```

然后启动时自动应用。

### 10.3 数据库连接失败

**症状**：`web-backend` 错误日志出现：

```text
java.sql.SQLNonTransientConnectionException: Public Key Retrieval is not allowed
```

**原因**：MySQL 8.0 在某些认证配置下会限制客户端公钥检索，导致 JDBC 连接初始化失败。

**解决方案**：通过自定义环境变量 `JDBC_PARAMETER` 调整 JDBC URL 参数，确保包含 `allowPublicKeyRetrieval=true`。

在项目根目录 `.env` 文件中设置（或覆盖）如下参数：

```properties
JDBC_PARAMETER=useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

或根据数据库实际服务配置需求调整这项参数

## 11. 实战案例

### 案例 1：开发环境快速启动（使用阿里云 Maven）

创建 `.env` 文件：

```properties
APP_TAG=dev
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
MYSQL_ROOT_PASSWORD=dev123456
MYSQL_DATABASE=xyy_dev
```

启动：

```bash
docker-compose up -d
# 查看日志
docker-compose logs -f web-backend
```

### 案例 2：内网部署（使用私有 Nexus 仓库）

编辑 `.env`：

```properties
APP_TAG=internal-v1.0
MAVEN_MIRROR_URL=http://nexus.internal.com:8081/repository/maven-public/
MYSQL_ROOT_PASSWORD=StrongPassword@2024
MYSQL_DATABASE=xyy_prod
```

构建并启动：

```bash
docker-compose build
docker-compose up -d
# 验证服务
docker-compose ps
docker-compose logs web-backend
```

### 案例 3：多环境配置（使用 compose 覆盖文件）

保持 `docker-compose.yml` 不变，创建 `docker-compose.prod.yml`：

```yaml
version: '3.8'

services:
  web-backend:
    environment:
      DB_HOST: mysql-prod.example.com
      REDIS_HOST: redis-prod.example.com
      JDBC_PARAMETER: useSSL=true&serverTimezone=UTC&allowPublicKeyRetrieval=true&maxPoolSize=50
    ports:
      - "8087:8087"
```

生产环境启动：

```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## 12. 相关文档

- [Dockerfile 构建](dockerfile.md)
- [项目构建](../build.md)
- [运行部署](../../quick-start/index.md)
- [配置参数](../../quick-start/config.md)
- [Docker Compose 官方文档](https://docs.docker.com/compose/)
