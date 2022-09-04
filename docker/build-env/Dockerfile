# 创建编译构建环境
FROM ubuntu
MAINTAINER xiaotao "mjt233@qq.com"

# 安装构建依赖
RUN sed -i "s/archive.ubuntu.com/mirrors.aliyun.com/g" /etc/apt/sources.list && \
    apt update && \
    apt install -y --no-install-recommends git openjdk-11-jdk-headless maven

# 复制maven配置
COPY ./conf/maven-settings.xml /etc/maven/settings.xml

# 克隆项目
WORKDIR /root
RUN git clone https://github.com/mjt233/saltedfishcloud-backend.git && cd saltedfishcloud-backend && git checkout develop
WORKDIR saltedfishcloud-backend

# 复制预构建的配置文件
COPY ./conf/config.yml /root/saltedfishcloud-backend/pre-release/config.yml

# 开始构建
RUN mvn -T `cat /proc/cpuinfo| grep "processor"| wc -l` clean install package