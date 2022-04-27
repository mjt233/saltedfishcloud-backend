# 项目构建

该文档将指导您如何通过源代码和maven项目工程来编译、运行和打包构建咸鱼云网盘项目

## 0. 拉取代码

这一步比较简单，会使用Git的应该都会这一步
```shell
git clone https://github.com/mjt233/saltedfishcloud-backend
# 或者使用gitee仓库链接
git clone https://gitee.com/xiaotao233/saltedfishcloud-backend
```

## 1. 切换分支

一般情况下，master分支是相对稳定的发布分支。而develop分支可能可以体验到master分支没有的新功能、bug修复、以及可能更多的bug。

develop分支提交可能会比较频繁，往往会不稳定。
```shell
# 切换到master
git checkout master
# 切换到develop
git checkout develop
```


## 2. 前端项目打包集成

如果你需要将前端项目集成到SpringBoot后端资源中，可按照以下步骤进行配置。

1. 配置咸鱼云后端根项目(saltedfishcloud)的pom.xml中的`sfc.front-end-path`参数为前端项目的本地路径
2. 在前端项目中使用`npm run build`对前端项目进行编译打包（如已执行过打包可忽略该步，前端代码发生修改后需要重新编译）

完成上面的配置后，后面对模块saltedfishcloud或sfc-core执行maven的`compile`或`package`操作，即可将前端集成到sfc-core模块的`resource/webapp`中

> 如果你不需要集成前端项目，请注释掉pom.xml的`sfc.front-end-path`属性

## 3. 安装模块到本地maven仓库

如果你需要单独对某一模块进行`compile`或`package`操作，就需要先将saltedfishcloud项目安装到本地仓库，执行maven的`install`即可。

参考命令：

```shell
mvn install
```
## 4. 打包发布

在saltedfishcloud模块执行`package`操作，将清空`release`目录，然后会将`pre-release`目录下的文件、`README.md`文件复制到`release`中，随后则是各个子模块的`package`动作。

sfc-core模块会将打包后的主程序jar包也复制到主模块的`release`下。其他拓展ext模块则会把自身的jar包复制到主模块的`release/ext-available`下

参考命令：
```shell
mvn package
```

> 如果你想打包时直接指定前端项目路径而不修改pom.xml，可在编译或打包的maven命令后面添加"-Dsfc.front-end-path=本地前端项目路径"