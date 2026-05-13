**接口路径**: `POST /api/openApi/diskFile/mkdir/v1`

**权限要求**: `storage_write`

**功能说明**: 在授权用户网盘的指定目录下创建新目录。

**请求格式**: `application/x-www-form-urlencoded` 或查询参数

**请求参数**:

| 参数名 | 类型   | 必填 | 说明           |
|--------|--------|------|----------------|
| `uid`  | number | 是   | 目标用户ID，`0` 表示公共网盘 |
| `path` | string | 是   | 父目录路径     |
| `name` | string | 是   | 新目录名称     |

**成功响应示例**:
```json
{
    "code": 200,
    "businessCode": 200,
    "msg": "OK",
    "data": null
}
```

