# 分层构建：创建编译构建环境
FROM maven:3.9.11-amazoncorretto-25-al2023 AS build
ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
WORKDIR /saltedfish
# 复制项目源代码到构建环境
COPY . /saltedfish
# 根据构建参数生成 Maven 配置文件并执行构建，默认使用 Maven 官方中央仓库
RUN set -eux; \
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
	mvn -s /tmp/maven-settings.xml install package

# 运行环境
FROM amazoncorretto:25-al2023
WORKDIR /saltedfish
# 从编译构建环境复制构建产物到运行环境
COPY --from=build /saltedfish/release/ .
# 复制咸鱼云的配置文件到运行环境
COPY ./conf/config.yml .
EXPOSE 8087
VOLUME [ "/saltedfish/store" ]
CMD java -jar sfc-core.jar --spring.config.import=file:config.yml
