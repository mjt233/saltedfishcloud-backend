**接口路径**: `POST /api/openApi/diskFile/rename/v1`

**权限要求**: `storage_write`

**功能说明**: 对用户网盘中的文件或目录进行重命名。

**请求格式**: `application/x-www-form-urlencoded` 或查询参数

**请求参数**:

| 参数名    | 类型   | 必填 | 说明             |
|-----------|--------|------|------------------|
| `uid`     | number | 是   | 目标用户ID，`0` 表示公共网盘 |
| `path`    | string | 是   | 文件所在目录路径 |
| `oldName` | string | 是   | 原文件名         |
| `newName` | string | 是   | 新文件名         |

**成功响应示例**:
```json
{
    "code": 200,
    "businessCode": 200,
    "msg": "OK",
    "data": null
}
```

