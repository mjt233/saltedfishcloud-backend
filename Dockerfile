# 创建编译构建环境
FROM maven:latest AS build

WORKDIR /saltedfish

COPY ./conf/maven-settings.xml /usr/share/maven/conf/settings.xml

COPY . /saltedfish

RUN mvn install package

FROM openjdk:8-jre-alpine

WORKDIR /saltedfish

COPY --from=build /saltedfish/release/ .

COPY ./conf/config.yml .

CMD java -jar sfc-core.jar --spring.config.import=file:config.yml
