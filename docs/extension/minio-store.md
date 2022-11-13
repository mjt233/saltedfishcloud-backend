# minio-store

## 简介

minio-store拓展为咸鱼云系统提供对MinIO对象存储文件系统读写支持，该插件提供以下存储支持：

- 网盘主存储
- 挂载目录

## 主存储配置说明

```yaml
sys:
  store:
    # 设置存储类型为hdfs
    type: minio

    # minio存储配置
    minio:
      access-key: xyy
      secret-key: xyy123456
      bucket: xyy


```