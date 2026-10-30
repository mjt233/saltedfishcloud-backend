# syntax=docker/dockerfile:1
#
# 咸鱼云离线下载微服务镜像：sfc-download-service（HTTP 8092）
#
# ⚠️ 构建上下文必须是【仓库根目录】。本模块依赖根 reactor 里的共享库
#    （sfc-common / sfc-common-data / sfc-auth / sfc-local-registry-client / sfc-remote-client /
#     sfc-task-worker），所以构建阶段用 `-pl :sfc-download-service -am` 在根 reactor 内解析。
#
# 构建（在仓库根目录执行）：
#   docker build -f microservices/sfc-download-service/Dockerfile -t saltedfishcloud/sfc-download-service:latest .
#
# 运行（服务发现/注册参数全部走环境变量，无需修改 application.yml）：
#   docker run -d --name sfc-download-service -p 8092:8092 \
#     -e SFC_INTERNAL_KEY=<与 core 一致的共享密钥，至少 32 字符> \
#     -e SFC_LOCAL_REGISTRY_ADDR=host.docker.internal:8087 \
#     -e SFC_LOCAL_REGISTRY_PASSWORD=<与 core 的 sys.service.local-registry.password 一致> \
#     -v sfc-download-data:/data \
#     saltedfishcloud/sfc-download-service:latest
#   改用 Nacos 注册中心：
#     -e SFC_LOCAL_ENABLED=false -e NACOS_ENABLED=true -e NACOS_SERVER_ADDR=<nacos 地址>:8848
#   ⚠️ 离线下载的 aria2 / Git 业务参数由 core 的管理端「插件配置」统一管理，不在本镜像里配置。

# ============================== 构建阶段 ==============================
FROM maven:3.9.11-amazoncorretto-25-al2023 AS build
# 国内网络可换成镜像仓库地址，例如 https://maven.aliyun.com/repository/public
ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
WORKDIR /build
COPY . /build
RUN --mount=type=cache,target=/root/.m2 \
    set -eux; \
    printf '%s\n' \
        '<?xml version="1.0" encoding="UTF-8"?>' \
        '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' \
        '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' \
        '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
        '    <mirrors>' \
        '        <mirror>' \
        '            <id>build-mirror</id>' \
        '            <mirrorOf>*</mirrorOf>' \
        '            <name>Build Mirror</name>' \
        "            <url>${MAVEN_MIRROR_URL}</url>" \
        '        </mirror>' \
        '    </mirrors>' \
        '</settings>' \
        > /tmp/maven-settings.xml; \
    mvn -B -s /tmp/maven-settings.xml -pl :sfc-download-service -am -DskipTests package; \
    cp microservices/sfc-download-service/target/sfc-download-service-*.jar /build/app.jar

# ============================== 运行阶段 ==============================
FROM amazoncorretto:25-al2023
WORKDIR /app

# 健康检查与排障依赖 curl：基础镜像不保证自带，显式安装（约 5MB）
RUN dnf install -y curl-minimal && dnf clean all

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8" \
    SFC_DOWNLOAD_DB_URL="jdbc:sqlite:/data/download.db?journal_mode=WAL&busy_timeout=5000"

COPY --from=build /build/app.jar /app/app.jar

# SQLite 数据目录（download.db 及 WAL 文件），容器重建后任务数据不丢
VOLUME ["/data"]
EXPOSE 8092

# 存活探针：命中静态资源即说明 HTTP 服务已就绪（本服务未引入 actuator，故不用 /actuator/health）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8092/static/index.umd.js || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
