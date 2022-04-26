# 简介

## 简要说明
程序会读取 `程序运行路径/ext/` 下的jar文件作为拓展插件来加载。
对sfc-ext项目执行package后，拓展jar包会在/release/ext-available/中生成，若有需要将其复制到`程序运行路径/ext/`即可

## 缺陷说明
- 暂未对拓展插件做管理工作，只是简单地使用URLClassLoader把jar包加载后将该URLClassLoader设置为线程上下文加载器，不保证所有插件都能正常工作。
- 该项目中的sfc-ext-hadoop-store拓展就可能会无法正常被加载，若有需要请将该项目添加到sfc-core的依赖中再打包。
- 拓展问题后续会不断完善