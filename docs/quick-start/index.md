# 起步

## 0. 打包与编译
对根项目执行maven的package，执行成功后会在release目录下创建程序主程序jar包和相关文件
创建release后，目录结构如下：
```
release
    ├── README.md                           - 自述文件
    ├── config.yml                          - 系统程序配置文件
    ├── ext                                 - 该目录下的jar包将被加载到系统
    ├── ext-available                       - 存放可用的拓展，拓展项目打包后会将其jar包复制到该处
    │       ├── sfc-ext-demo.jar            - 拓展演示demo
    │       └── sfc-ext-mp3-thumbnail.jar   - 让系统获得对mp3专辑缩略图提取支持
    ├── sfc-core.jar                        - 咸鱼云网盘主程序核心
    ├── start.bat                           - Windows用的bat启动脚本
    └── start.sh                            - Linux/Mac 下 bash/dash 用的启动脚本

```

## 1. 准备环境
项目依赖以下组件：

- MySQL
- Redis

## 2. 运行项目

### 方式0：启动命令行指定外部spring yml配置文件**（推荐）**
这个是推荐的启动方式，配置和启动也非常简单。
将主程序jar包同目录下的config.yml配置完成后，直接执行下面的命令即可启动。
当然你也可以修改file:config.yml这个参数指定任意路径的yml配置文件。
```shell
# 以下为start.sh的内容，是最简单的启动命令
java -jar sfc-core.jar --spring.config.import=file:config.yml
```
配置文件config.yml的配置内容与spring的application.yml完全一致且会覆盖默认的application.yml的内容。而config.yml中未配置的项将取application.yml的配置项作为默认值

### 方式1：通过预设命令行变量的启动脚本<font color="red"> （已弃用）</font>
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

## 3. 完成

至此，启动程序后便完成了后端服务器的部署，此时通过浏览器URL访问，可以看到

**咸鱼云网盘后端正在运行...**

这个页面，出现该页面说明程序运行成功😆

> 注：如果你是通过release页面下载的已经打包编译好的程序，此时应该可以直接访问到完整的功能页面。

该服务仅能提供后端API服务，您还需要对前端页面程序进行部署。或者通过其他接入该API系统的客户端（虽然我没开发:p，API接口文档见"咸鱼云PostmanAPI文档"）

系统默认创建的管理员账号信息如下：


| 用户名    | 密码        |
|:-------|:----------|
| admin  | admin666  |

> **注意：完成系统部署后请务必修改管理员账号密码**

