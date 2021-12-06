@echo off
copy ..\sfc-web\\src\main\resources\application.sample.yml ..\sfc-web\\src\main\resources\application.yml
if exist ..\target\*.jar (
	del ..\target\*.jar
)
cd ..\sfc-compress
call mvn clean
call mvn install
cd ..\sfc-web
call mvn clean
call mvn install
cd ..
call mvn package
if errorlevel 1 (
    echo 构建失败
) else (
    echo 构建完成，下一步可以参考start.bat.template文件配置项目启动脚本来启动项目了
)