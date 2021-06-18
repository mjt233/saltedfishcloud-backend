#!/bin/sh
# 应用程序参数设置

# server_port  服务器端口 
# public_root  公共网盘存储位置 
# store_root 私人网盘及用户数据存储位置 
# store_type 初始存储方式，可选unique或raw 
# reg_code 注册邀请码 
# sync_delay  同步延迟，单位分支，-1关闭 
# sync_launch  启动后立即同步 
# ftp_port  FTP服务端口
server_port=8087
public_root=data/public
store_root=data/xyy
store_type=unique  
reg_code=10241024  
sync_delay=5   
sync_launch=false  
ftp_port=21

# 数据源设置
db_host="127.0.0.1"
db_port="3306"
db_name="xyy"
db_username="root"
db_password=""
db_params="useSSL=false&serverTimezone=UTC"

redis_host="127.0.0.1"
redis_port="6379"


jdbc_url="jdbc:mysql://${db_host}:${db_port}/${db_name}?${db_params}"

java -Dfile.encoding=utf-8 -jar ../target/saltedfishcloud-1.0.0-SNAPSHOT.jar \
--server.port=$server_port \
--spring.datasource.druid.url="$jdbc_url" \
--spring.datasource.druid.username="$db_username" \
--spring.datasource.druid.password="$db_password" \
--spring.datasource.redis.host="$redis_host" \
--spring.datasource.redis.part="$redis_port" \
--spring.datasource.redis.password="" \
--public-root="$public_root" \
--store-root="$store_root" \
--store-type="$store_type" \
--RegCode=$reg_code \
--sync-delay=$sync_delay \
--sync-launch=$sync_launch \
--ftp-port=$ftp_port