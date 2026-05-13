**接口路径**: `DELETE /api/openApi/diskFile/delete/v1`

**权限要求**: `storage_write`

**功能说明**: 删除用户网盘指定目录下的文件或目录。

**请求参数** (Query):

| 参数名 | 类型   | 必填 | 说明               |
|--------|--------|------|--------------------|
| `uid`  | number | 是   | 目标用户ID，`0` 表示公共网盘 |
| `path` | string | 是   | 文件所在目录路径   |

**请求体** (`application/json`):

| 字段名     | 类型     | 必填 | 说明               |
|------------|----------|------|--------------------|
| `fileName` | string[] | 是   | 待删除的文件名列表 |

**请求示例**:
```json
{
    "fileName": ["old_backup.zip", "temp_folder"]
}
```

**成功响应示例**:
```json
{
    "code": 200,
    "businessCode": 200,
    "msg": "OK",
    "data": 2
}
```

**说明**: `data` 为实际删除的文件/目录数量。

