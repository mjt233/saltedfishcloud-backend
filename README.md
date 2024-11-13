# 基于SpringBoot实现的咸鱼云网盘-后端

![](https://img.shields.io/badge/SpringBoot-3.3.4-green.svg)
![](https://img.shields.io/badge/Java-17-green.svg)

---

更多内容请参考项目[在线文档](https://mjt233.github.io/saltedfishcloud-backend/) (逐步完善中)

## 提示 

该项目仅为后端，不带前端，前端项目请移步[Gitee](https://gitee.com/xiaotao233/saltedfishcloud-frontend) 或 [GitHub](https://github.com/mjt233/saltedfishcloud-backend)

## 项目介绍

### 简介

咸鱼云网盘目前是一个用于共享文件和实现私人网盘基本功能的系统，同时具有公共网盘与私人网盘，公共资源站与私有存储云两不误。

### 功能介绍

#### 基础功能

- 公共网盘与私人网盘两个存储域
- 文件搜索
- 文件收集
- 文件/目录分享
- 外部存储挂载
- 在线解压缩
- 默认按文件哈希散列统一组织文件，支持文件秒传
- 自定义配置桌面组件
- 文本文件在线编辑，markdown编辑实时预览
- 视频在线播放

#### 其他技术特性  

- 具有插件系统
- 兼容低版本到高版本的升级，自动更新数据库
- 构建与部署简单，具有统一的属性参数配置系统，大部分参数都能在运行期间通过管理员界面进行动态配置。
- 支持docker部署（文档待补充）

#### 拓展功能（插件支持）

- 发布目录为静态页面站点
- WebShell
- 支持存储集群
- 外部存储目录挂载 - OSS对象存储, MinIO, HDFS, SFTP, FTP协议的外部存储读写支持
- 自定义视频转码、字幕提取
- 作为FTP服务端提供网盘文件访问
- 通过ONLYOFFICE支持office文档在线编辑和预览

## 杂杂念

该项目是我大二时从无Java基础一边学习一边开发一边重构和维护的项目，难免会有明显bug或明显的设计缺陷。

欢迎各路大佬提出批评、建议和issue。也欢迎感兴趣的大佬贡献代码。

## 快速开始    

### 0. 打包与编译

全模块打包直接使用package命令即可

```shell
$ mvn package
```

如果单独是对某个拓展模块打包，需要先install sfc-api模块。（若全模块打包失败也可先install sfc-api模块）
```shell
$ cd sfc-api; mvn install;
$ cd ../sfc-ext/sfc-ext-demo; mvn package
```
输出目录：
- 主程序: `release/sfc-core.jar`
- 拓展插件：`release/ext-available/*.jar`

### 1. 运行程序

启动前需要在配置文件`config.yml`确认MySQL数据库与Redis连接配置，确认或修改无误后

基础启动命令：
```shell
$ java -jar sfc-core.jar --spring.config.import=file:config.yml
```


### 2 关于数据表

- JPA自动完成数据表的创建和更新，无需手动操作

### 3. 可选插件

位于sfc-ext模块下，打包后各模块jar包在`release/ext-available`下，若要启用，将其复制到运行目录下的`ext`目录即可

> 注意：以下插件均为实验性功能，部分网络存储挂载功能尚不稳定。

**目前有以下插件：**

| 插件名            | 简介                                               |
|----------------|--------------------------------------------------|
| mp3-thumbnail  | 为mp3文件提供缩率图显示支持                                  |
| demo           | 没啥用，就是个demo，添加/ext/img和/ext/hello两个测试路由          |
| hadoop-store   | 提供hdfs文件系统读写支持（主存储、挂载存储）                         |
| oss-store      | 提供基于Amazon S3协议的OSS对象存储系统读写支持（挂载存储）              |
| minio-store    | **\[将并入oss-store\]** 提供minio对象存储系统读写支持（主存储、挂载存储） |
| sftp-store     | 提供基于SFTP文件传输的存储读写支持（挂载存储）                        |
| ftp-store      | 提供基于FTP文件传输的存储读写支持（挂载存储）                         |
| ftp-server     | 内嵌FTP服务器，支持通过FTP方式访问网盘系统的资源                      |
| video-enhance  | 基于ffmpeg的视频增强服务，支持播放选择字幕、视频转码功能                  |
| web-shell      | 通过管理员后台直接通过web界面进行服务器shell交互                     |
| static-publish | 将网盘的目录发布为独立的静态资源HTTP站点                           |
| quick-share    | 文件极速传，快捷匿名发送和下载文件                                |
| only-office    | 适配ONLYOFFICE，实现office文档在线预览和编辑                   |
| network-tools  | 服务器网络工具，用于在管理后台查看网络接口信息，并给用户提供WOL支持              |


### 4. 插件的加载

#### jar包模式

如果有已经打包好的插件（jar包），那么直接把插件放到`运行目录/ext`后，启动主程序即可


#### 开发模式

在maven的`develop`配置文件环境下，对`application-develop.yml`的`plugin.extra-resource`数组补充`sfc-ext/插件项目`，如：
```yaml
plugin:
  extra-resource:
    - sfc-ext/sfc-ext-demo
    - sfc-ext/sfc-ext-ftp-server
```

tips：
1. 插件项目需要使用`sfc-ext`作为父级，并确保本地仓库安装了`sfc-api`
2. 插件项目初创或修改了`pom.xml`后，需要执行`mvn clean compile`。
3. 启动主程序之前，若修改了插件项目的代码，需要手动构建后再启动主程序，否则加载插件时不会加载到修改后的代码
4. 不要把插件项目添加到主项目的maven依赖中
5. 在IntelliJ IDEA中，插件的代码编译修改后，支持热重载class（其他IDE未测试）

## 部分前端界面展示

- 支持自定义配置的首页
  ![](./docs/img/main.png)
  ![](./docs/img/desktop-config.png)
- 目录浏览支持README.md渲染和在线编辑
  ![](./docs/img/main2.png)
- 管理员后台-简单的插件系统
  ![](./docs/img/plugin.png)