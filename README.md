# 基于SpringBoot实现的咸鱼云网盘-后端
![](https://img.shields.io/badge/SpringBoot-2.4-green.svg)
![](https://img.shields.io/badge/Java->=1.8-green.svg)

## 提示 
该项目仅为后端，不带前端，前端项目请移步[这里](https://gitee.com/xiaotao233/saltedfishcloud-frontend)

## 项目介绍
### 简介
咸鱼云网盘目前是一个用于共享文件和实现私人网盘基本功能的系统，目前处于初期开发阶段，系统尚不稳定，许多功能和文档尚未完善。  
### 特性  
- 同时具有公共网盘与私人网盘，公共资源站与私有存储云两不误
- 相同文件只存一份，硬盘空间利用率高
- 文件下载直链极速分享  
- 文件索引与缓存，海量文件搜索快速响应
- 支持代理选择，自定义参数的离线下载
- 文件收集
- 文件分享
- 支持FTP访问（实验性功能，可能不稳定）

### 项目近期开发可能实现的特性  
- 目录共享
- 在线解压缩
- 模块化外部插件支持
- 动态加载外部自定义文件系统和存储服务（基于模块化外部拓展插件，可自定义拓展使用Hadoop，MongoDB存储）

### 未来企划
- 针对实体数据信息类进行规范性重构
- 微服务化重构
- Docker部署

### 项目计划实现的基本特性（可能会咕咕咕）
- 分布式存储
- WebDav访问支持
- Samba访问支持
- 多媒体播放列表
- 自动备份
- 网盘转存
- 挂载外部存储（外部FTP，Samba，sftp等）

## 快速开始    

### 初始化数据库
手动创建好空数据库后，只需在`script`目录中的`start.sh`或`start.bat`配置好数据库名，咸鱼云第一次启动时将自动初始化数据表  
> 注意：需要保证数据库为空，不含任何数据表

### 构建项目
```shell
cd script
./build
```
### 运行项目
修改start.bat或start.sh的程序属性变量后执行以下shell命令即可
```shell
cd script
cp start.sh.template start.sh
vim start.sh # 可根据实际需求修改start.sh中的参数
./start.sh
```

### 命令行选项
- --switch 可选值:RAW/UNIQUE,启动存储切换程序，一般用于低版本UNIQUE模式下升级到新版本以解决兼容性问题，以bash下为例：
    ```shell script
    项目目录/script@localhost $ start.sh --switch=RAW
    ```
  注意：对数据而言这是高危操作，仍然建议提前备份好数据防止丢失

### 协议支持
- FTP（实验性功能预览）  
    1. 默认开启和使用21端口，可通过参数`ftp-port`进行修改  
    2. Linux下非root用户请使用大于1024的端口号（不建议以root用户身份或通过sudo运该项目），但可利用端口转发实现21端口到FTP端口的转发
    3. 外部网络设备需要访问FTP时，请检查`ftp_passive_addr`参数是否为用户可访问的地址，默认是localhost，外部网络设备访问时会出现连接错误
- WebDav  
    暂不支持，未来版本开发
- Samba  
    暂不支持

### 注意事项
唯一存储模式（UNIQUE）下，绝对不要使用咸鱼云以外的手段对网盘文件内容进行修改或内容追加操作。  
由于文件硬链接的特性，该操作会导致咸鱼云(所有用户所有目录下与被修改的文件相同)的文件一并被修改，同时会导致文件MD5记录异常

### 接口测试
可将`postman_collection.json`文件导入到`postman`中进行测试
