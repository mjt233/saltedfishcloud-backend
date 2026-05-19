**接口路径**: `POST /api/openApi/diskFile/move/v1`

**权限要求**: `storage_write`

**功能说明**: 将文件或目录移动到用户网盘的目标目录。

**请求格式**: `application/json`

**请求参数** (Query):

| 参数名 | 类型   | 必填 | 说明 |
|--------|--------|------|------|
| `uid`  | number | 是   | 目标用户ID，`0` 表示公共网盘 |

**请求体**:

| 字段名       | 类型     | 必填 | 说明                                |
|--------------|----------|------|-------------------------------------|
| `sourcePath` | string   | 是   | 文件当前所在目录路径                |
| `files`      | string[] | 是   | 待移动的文件名列表                  |
| `targetPath` | string   | 是   | 目标目录路径                        |
| `isOverwrite`| boolean  | 否   | 是否覆盖同名文件，默认 `false`      |

**请求示例**:
```json
{
    "sourcePath": "/downloads",
    "files": ["report.pdf"],
    "targetPath": "/documents",
    "isOverwrite": true
}
```

**成功响应示例**:
```json
{
    "code": 200,
    "businessCode": 200,
    "msg": "OK",
    "data": null
}
```

