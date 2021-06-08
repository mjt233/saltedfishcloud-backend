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
echo 构建完成，下一步可以修改start.bat脚本配置项目启动参数来启动项目了