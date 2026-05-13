**接口路径**: `GET /api/openApi/diskFile/fileList/v1`

**权限要求**: `storage_read`

**功能说明**: 获取授权用户网盘指定目录下的文件与子目录列表。

**请求参数**:

| 参数名 | 类型   | 必填 | 说明                        |
|--------|--------|------|-----------------------------|
| `uid`  | number | 是   | 目标用户ID，`0` 表示公共网盘 |
| `path` | string | 否   | 目录路径，默认为根目录 `/`  |

**成功响应示例**:
```json
{
    "code": 200,
    "businessCode": 200,
    "msg": "OK",
    "data": [
        {
            "name": "documents",
            "size": 0,
            "md5": null,
            "type": "DIR",
            "ctime": 1715000000000,
            "mtime": 1715000000000
        },
        {
            "name": "photo.jpg",
            "size": 204800,
            "md5": "d41d8cd98f00b204e9800998ecf8427e",
            "type": "FILE",
            "ctime": 1715000000000,
            "mtime": 1715000000000
        }
    ]
}
```
