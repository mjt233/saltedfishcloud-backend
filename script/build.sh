cp ../sfc-web/src/main/resources/application.sample.yml ../sfc-web/src/main/resources/application.yml
rm ../sfc-web/target/*.jar 2>&1 > /dev/null

cd ../sfc-compress
mvn clean
mvn install
cd ../sfc-web
mvn clean
mvn install
cd ..
mvn package
if [ $? == 0 ]; then
	echo "构建完成，下一步可以参考start.sh.template文件配置项目启动脚本来启动项目了"
else
	echo "构建失败"
fi