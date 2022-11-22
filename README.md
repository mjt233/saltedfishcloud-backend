# 基于SpringBoot实现的咸鱼云网盘-后端

![](https://img.shields.io/badge/SpringBoot-2.4-green.svg)
![](https://img.shields.io/badge/Java-11-green.svg)

---

更多内容请参考项目[在线文档](https://mjt233.github.io/saltedfishcloud-backend/)

## 提示 

该项目仅为后端，不带前端，前端项目请移步[Gitee](https://gitee.com/xiaotao233/saltedfishcloud-frontend) 或 [GitHub](https://github.com/mjt233/saltedfishcloud-backend)

## 项目介绍

### 简介

咸鱼云网盘目前是一个用于共享文件和实现私人网盘基本功能的系统，同时具有公共网盘与私人网盘，公共资源站与私有存储云两不误。

具有支持代理的离线下载，FTP访问，在线解压缩，文件搜索，文件收集，文件/目录分享，OSS对象存储挂载等功能。

在存储上支持原始目录存储（RAW）与相同文件集中存储（UNIQUE）两种文件存储组织模式，并能随时切换（不推荐的操作）。

### 其他特性  

- 兼容低版本到高版本的升级，自动更新数据库
- 构建与部署简单，大部分参数都能在运行期间通过管理员界面进行动态配置。
- 支持docker部署（文档待补充）
- 插件化动态功能拓展
- 支持存储集群（需要MinIO或HDFS存储插件）
- 外部存储目录挂载（除本地文件系统存储外，需要通过插件可提供MinIO,HDFS,Samba,SFTP协议的外部存储读写支持）

## 杂杂念

该项目是我大二时从无Java基础一边学习一边开发一边重构和维护的项目，难免会有明显bug或明显的设计缺陷。

对项目有任何疑问或建议，欢迎各路大佬评论或提出issue。

如果你对我这个玩具项目感兴趣，也想参与项目开发，可以fork该项目，发起Pull Request，审核和测试通过后将合并你的代码。

## 快速开始    

### 0. 打包与编译

对根项目执行maven的package，执行成功后会在release目录下创建程序主程序jar包和相关文件

### 1. 准备环境

项目依赖以下组件：
- MySQL
- Redis

### 2. 运行项目

#### 2.1 启动程序

将主程序jar包同目录下的config.yml配置完成后，直接执行下面的命令即可启动。

当然你也可以修改file:config.yml这个参数指定任意路径的yml配置文件。

```shell
# 最简单的启动命令
java -jar sfc-core.jar --spring.config.import=file:config.yml
```

配置文件config.yml的配置内容与spring的application.yml完全一致且会覆盖默认的application.yml的内容。而config.yml中未配置的项将取application.yml的配置项作为默认值

### 3 关于数据表

- 项目启动后会自动初始化数据库。若初始化失败，可尝试手动给数据库执行初始化脚本，脚本位于`sfc-core/src/main/resource/sql/full.sql`
- 目前只在MySQL上测试过系统，不确保其他数据库管理系统可以正常运行

### 3. 可选插件

位于sfc-ext模块下，打包后各模块jar包在`release/ext-available`下，若要启用，将其复制到运行目录下的`ext`目录即可

**目前有以下插件：**

| 插件名           | 简介                                      |
|---------------|-----------------------------------------|
| mp3-thumbnail | 为mp3文件提供缩率图显示支持                         |
| demo          | 没啥用，就是个demo，添加/ext/img和/ext/hello两个测试路由 |
| hadoop-store  | 提供hdfs文件系统读写支持（主存储、挂载存储）                |
| minio-store   | 提供minio对象存储系统读写支持（主存储、挂载存储）             |
| samba-store   | 提供Samba文件共享的读写支持（挂载存储）                  |
| sftp-store    | 提供基于SFTP文件传输的存储读写支持（挂载存储）               |
