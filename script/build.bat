@echo off
if exist ..\src\main\resources\application.yml (
	echo application.xml已存在
) else (
	echo 生成application.xml
	copy ..\src\main\resources\application.simple.yml ..\src\main\resources\application.yml
)
if exist ..\target\*.jar (
	del ..\target\*.jar
)
cd ..
call mvn package
if errorlevel 1 (
    echo 构建失败
) else (
    echo 构建完成，下一步可以参考start.bat.template文件配置项目启动脚本来启动项目了
)