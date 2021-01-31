# 基于SpringBoot实现的咸鱼云网盘-后端
![](https://img.shields.io/badge/SpringBoot-2.4-green.svg)
![](https://img.shields.io/badge/Java->=1.8-green.svg)

## 提示 
该项目仅为后端，不带前端，前端项目请移步[这里](https://gitee.com/xiaotao233/saltedfishcloud-frontend)

## 项目介绍
### 简介
咸鱼云网盘目前是一个用于共享文件和实现私人网盘基本功能的系统，目前仍处于原型开发阶段，许多功能和文档尚未完善。  
### 特性  
- 有公共公开网盘功能，可作为资源站
- 私人网盘功能
- 前后端分离，可编写多个前端项目实现全平台支持  
### 项目近期开发可能实现的特性  
1. 添加支持利用md5 整个网盘系统中相同md5的文件只存一份
2. 可选直接目录存储或使用数据库实现用户目录结构而不是本地物理目录结构（需要1的支持）并提供两者之间的转换功能
3. 添加文件复制，重命名，移动支持
4. 使用命令行参数设定各目录和数据库连接参数或首次启动后通过Web面板在线设置，实现开箱即用
### 项目计划实现的基本特性（可能会咕咕咕）
- 分享私人文件/目录
- 创建文件收集
- 离线下载
- 在线解压缩
- 分布式存储
- FTP访问支持
- WebDev访问支持
- Samba访问支持
- 多媒体播放列表
- 自动备份

## 快速开始    
### 修改项目配置  
1. 复制`application.simple.yml`为`application.yml`
2. 修改数据库jdbc连接地址，用户名和密码
3. 修改配置文件`config.properties`中的`public.root`和`private.root`，这两个值分别表示公共网盘根目录和私有网盘根目录，需要确保用户拥有这两个路径的读写权限（项目启动时若路径不存在，会尝试自动创建）

### 打包项目成jar
在项目目录中运行命令
```shell script
shell> mvn package
```
目录执行完成后jar文件将被创建在项目目录下的`/target`

### 创建和初始化数据库
```
mysql> SOURCE db.sql
```
### 运行项目
```shell script
shell> java -jar xxxx.jar --RegCode=123456 --public.root=D:/public --private.root=D:/private
# xxx.jar为打包后的/target下的jar文件
```

### 应用程序命令行参数说明
- RegCode - 注册邀请码，默认为114514，普通用户注册账号需要提供注册邀请码
- public.root - 公共网盘的根目录
- private.root - 私人网盘的根目录
