# hadoop-store

## 简介
> 注意：该插件为实验性插件，在小文件读写上hdfs性能十分糟糕


hadoop-store拓展为咸鱼云系统提供对hdfs文件系统的存储读写支持，使用Hadoop作为文件的存储后端。该插件提供以下存储支持：

- 网盘主存储
- 挂载目录

## 主存储配置说明

```yaml
sys:
  store:
#    设置存储类型为hdfs
    type: hdfs
    
#    hdfs连接配置选项
  hdfs:
    url: hdfs://localhost:9000 # hdfs连接url
    root: /xyy    # 咸鱼云的文件资源存储在hdfs上的根目录
    user: root    # hdfs登录用户名

```