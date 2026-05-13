**接口路径**: `POST /api/openApi/diskFile/copy/v1`

**权限要求**: `storage_write`

**功能说明**: 复制文件或目录到用户网盘的目标目录。

**请求格式**: `application/json`

**请求参数** (Query):

| 参数名 | 类型   | 必填 | 说明 |
|--------|--------|------|------|
| `uid`  | number | 是   | 目标用户ID，`0` 表示公共网盘 |

**请求体**:

| 字段名       | 类型          | 必填 | 说明                                               |
|--------------|---------------|------|----------------------------------------------------|
| `sourcePath` | string        | 是   | 源文件所在目录路径                                 |
| `files`      | string[]      | 否   | 待复制的文件名列表，为 `null` 时复制源目录全部文件 |
| `targetPath` | string        | 是   | 目标目录路径                                       |
| `isOverwrite`| boolean       | 否   | 是否覆盖同名文件，默认 `false`                     |

**请求示例**:
```json
{
    "sourcePath": "/photos",
    "files": ["vacation.jpg", "birthday.png"],
    "targetPath": "/backup/photos",
    "isOverwrite": false
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

