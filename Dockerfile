# 创建编译构建环境
FROM maven:3.8.8-amazoncorretto-17-al2023 AS build

WORKDIR /saltedfish

COPY ./conf/maven-settings.xml /usr/share/maven/conf/settings.xml

COPY . /saltedfish

RUN mvn install package

FROM openjdk:17-ea-slim

WORKDIR /saltedfish

COPY --from=build /saltedfish/release/ .

COPY ./conf/config.yml .

CMD java -jar sfc-core.jar --spring.config.import=file:config.yml
