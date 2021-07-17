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
echo "构建完成，下一步可以修改start.sh脚本配置项目启动参数来启动项目了"