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
- 相同文件只存一份，硬盘空间利用率高
- 可生成文件下载直链  
### 项目近期开发可能实现的特性  
- 公共网盘支持唯一存储（UNIQUE）模式
- 离线下载
- FTP私人网盘的读写支持

### 项目计划实现的基本特性（可能会咕咕咕）
- 分享私人文件/目录
- 创建文件收集
- 离线下载
- 在线解压缩
- 分布式存储
- WebDev访问支持
- Samba访问支持
- 多媒体播放列表
- 自动备份

## 快速开始    

### 创建和初始化数据库
```
mysql> SOURCE db.sql
```

### 构建项目
```shell
cd script
./build
```
### 运行项目
修改start.bat或start.sh的程序属性变量后执行以下shell命令即可
```shell
cd script
./start
```

### 协议支持
- FTP（实验性功能预览）  
    1. 默认开启和使用21端口，可通过参数`ftp-port`进行修改  
    2. Linux下非root用户请使用大于1024的端口号（不建议以root用户身份或通过sudo运该项目），但可利用端口转发实现21端口到FTP端口的转发
    3. 暂时只支持公共网盘只读，后续开发会不断完善FTP的支持
- WebDev  
    暂不支持
- Samba  
    暂不支持

### 接口测试
可将`postman_collection.json`文件导入到`postman`中进行测试
