if [ -f ../src/main/resources/application.yml ]; then
	echo application.xml已存在
else 
	echo 生成application.xml
	cp ../src/main/resources/application.simple.yml ../src/main/resources/application.yml
fi

if [ -f ../target/*.jar ]; then
	rm ../target/*.jar
fi

cd ..
mvn package
if [ $? == 0 ]; then
	echo "构建完成，下一步可以参考start.sh.template文件配置项目启动脚本来启动项目了"
else
	echo "构建失败"
fi