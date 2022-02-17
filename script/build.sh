rm ../sfc-core/target/*.jar 2>&1 > /dev/null
cd ..
echo ================清理上次打包数据================
mvn clean
echo ================   开始打包    ================
mvn package
if [ $? == 0 ]; then
	echo "构建完成，下一步可以参考start.sh.template文件配置项目启动脚本来启动项目了"
else
	echo "构建失败"
fi