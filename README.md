# 基于SpringBoot实现的咸鱼云网盘-后端
![](https://img.shields.io/badge/SpringBoot-2.4-green.svg)
![](https://img.shields.io/badge/Java->=1.8-green.svg)

## 提示 
该项目仅为后端，不带前端，前端项目请移步[这里](https://gitee.com/xiaotao233/saltedfishcloud-frontend)

## 项目介绍
### 简介
咸鱼云网盘目前是一个用于共享文件和实现私人网盘基本功能的系统，同时具有公共网盘与私人网盘，公共资源站与私有存储云两不误。

具有支持代理的离线下载，FTP访问，在线解压缩，文件搜索，文件收集，文件/目录分享等功能。 

在存储上支持原始目录存储（RAW）与相同文件集中存储（UNIQUE）两种文件存储组织模式，并能随时切换（虽然有点危险）。

### 其他特性  
- 支持加载外部jar拓展（未来会继续完善拓展插件系统）
- 支持存储系统拓展，目前支持本地文件系统存储与hdfs（Hadoop）分布式存储（默认未集成，需要打包sfc-ext-hadoop-store模块jar包放到ext目录）
- 兼容低版本到高版本的升级，自动更新数据库
- 构建与部署简单，大部分参数都能在运行期间通过管理员界面进行动态配置。


## 杂杂念
该项目目前仅由我个人（学生）进行维护，是我从无基础（经验）一边学习一边开发一边重构和维护的项目，难免会有明显bug或明显的设计缺陷，对项目有任何疑问或建议，欢迎各路大佬评论或提出issue。

如果你对我这个玩具项目感兴趣，也想参与项目开发，可以fork该项目，发起Pull Request，审核和测试通过后将合并你的代码。

如果长时间未得到我的回复，可能是我刚好比较忙或忘了回复，可以试着给我发邮件。

### 我的近期工作安排
- 重写离线下载模块
- 完成分布式部署支持（目前剩余离线下载不支持分布式）
- 编写系统开发、配置和使用文档

## 快速开始    

### 0. 构建项目
```shell
cd script
./build
```

### 1. 准备环境
项目依赖以下组件：
- MySQL
- Redis

### 2. 运行项目

#### 方式1：启动命令行指定外部spring yml配置文件（推荐）
这个是推荐的启动方式，配置和启动也非常简单。
将主程序jar包同目录下的config.yml配置完成后，直接执行下面的命令即可启动。
当然你也可以修改file:config.yml这个参数指定任意路径的yml配置文件。
```shell
# 最简单的启动命令
java -jar sfc-core.jar --spring.config.import=file:config.yml
```
配置文件config.yml的配置内容与spring的application.yml完全一致且会覆盖默认的application.yml的内容。而config.yml中未配置的项将取application.yml的配置项作为默认值

#### 方式2：通过预设命令行变量的启动脚本（不推荐）
这是一开始没想到可以用方式1启动时编写的脚本启动方案。  
在script下修改start.bat.template或start.sh.template的程序属性变量后执行脚本即可  
参考命令：
```shell
cd script
cp start.sh.template start.sh
vim start.sh # 可根据实际需求修改start.sh中的参数
./start.sh
```
<font color="red">注意：如果连接的Redis服务器没有配置密码，请移除start脚本的`--spring.redis.password`所在的行</font>  

---

项目启动后会自动初始化数据库。若初始化失败，可尝试手动给数据库执行初始化脚本，脚本位于`sfc-core/src/main/resource/sql/full.sql`

### 协议支持
- FTP（实验性功能预览）  
    1. 默认开启和使用21端口，可通过参数`ftp-port`进行修改  
    2. Linux下非root用户请使用大于1024的端口号（不建议以root用户身份或通过sudo运该项目），但可利用端口转发实现21端口到FTP端口的转发
    3. 外部网络设备需要访问FTP时，请检查`ftp_passive_addr`参数是否为用户可访问的地址，默认是localhost，外部网络设备访问时会出现连接错误
- WebDav  
    暂不支持，未来版本开发
- Samba  
    暂不支持

## 未来企划
- 第三方登录
- 开发更完善的用户组织架构和权限管理机制
- 微服务化重构
- Docker部署
- 开发多平台客户端，支持挂载到系统资源管理器
- 完善的插件拓展体系（类似VS Code），并具有在线拓展管理系统，动态拓展和定制系统功能
- 作为私有人云服务器以文件为主服务集成系统（比如通过咸鱼云对Minecraft服务器进行管理，通过咸鱼云部署静态/动态网页或站点）

## 项目计划实现的基本特性（可能会咕咕咕）
- WebDav访问支持
- Samba访问支持
- 多媒体播放列表
- 自动备份
- 网盘转存
- 挂载外部存储（外部FTP，Samba，sftp等）

## 接口文档/测试
可将`postman_collection.json`文件导入到`postman`中查看文档和进行测试
