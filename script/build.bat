@echo off
if exist ..\target\*.jar (
	del ..\target\*.jar
)
cd ..
echo ================清理上次打包数据================
call mvn clean
echo ================   开始打包    ================
call mvn package
if errorlevel 1 (
    echo 构建失败
) else (
    echo 构建完成，下一步可以参考start.bat.template文件配置项目启动脚本来启动项目了
)