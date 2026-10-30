# syntax=docker/dockerfile:1
#
# 咸鱼云独立网关镜像：sfc-gateway（Spring Cloud Gateway Server WebFlux，HTTP 9090）
#
# 网关是微服务模式的整站唯一入口：浏览器只与它对话，由它把 SPA / /api/** / WebSocket 透传给 core，
# 并把 /api/service/{服务名}/** 代理到各微服务。它自己也是注册中心的客户端（服务发现 + 负载均衡）。
#
# ⚠️ 构建上下文必须是【仓库根目录】（本模块依赖根 reactor 里的 sfc-common / sfc-auth /
#    sfc-local-registry-client），构建阶段用 `-pl :sfc-gateway -am` 在根 reactor 内解析。
#
# 构建（在仓库根目录执行）：
#   docker build -f microservices/sfc-gateway/Dockerfile -t saltedfishcloud/sfc-gateway:latest .
#
# 运行（服务发现/注册参数全部走环境变量，无需修改 application.yml）：
#   docker run -d --name sfc-gateway -p 9090:9090 \
#     -e SFC_JWT_SECRET=<core 的 sys.safe.token，必须恰好 32 字符> \
#     -e SFC_LOCAL_REGISTRY_ADDR=host.docker.internal:8087 \
#     -e SFC_LOCAL_REGISTRY_PASSWORD=<与 core 的 sys.service.local-registry.password 一致> \
#     saltedfishcloud/sfc-gateway:latest
#   ⚠️ SFC_JWT_SECRET 缺失或长度不是 32 时网关【启动即失败】（fail-fast）；它与 SFC_INTERNAL_KEY
#      是两把不同的密钥，不要混用。
#   改用 Nacos 注册中心：
#     -e SFC_LOCAL_ENABLED=false -e NACOS_ENABLED=true -e NACOS_SERVER_ADDR=<nacos 地址>:8848

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
    mvn -B -s /tmp/maven-settings.xml -pl :sfc-gateway -am -DskipTests package; \
    cp microservices/sfc-gateway/target/sfc-gateway-*.jar /build/app.jar

# ============================== 运行阶段 ==============================
FROM amazoncorretto:25-al2023
WORKDIR /app

# 健康检查与排障依赖 curl：基础镜像不保证自带，显式安装（约 5MB）
RUN dnf install -y curl-minimal && dnf clean all

# 大请求体（上传/下载代理）会占用堆外直接内存，如有需要可追加 -XX:MaxDirectMemorySize=<size>
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

COPY --from=build /build/app.jar /app/app.jar

EXPOSE 9090

# 网关已引入 spring-boot-starter-actuator，直接用健康端点做探针
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://127.0.0.1:9090/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
