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
            "id": "1054223881344122880",
            "uid": 1054223881344122880,
            "createAt": "2024-05-06T12:00:00.000+00:00",
            "updateAt": "2024-05-06T12:00:00.000+00:00",
            "node": "abc123def456abc1",
            "mtime": 1715000000000,
            "ctime": 1715000000000,
            "name": "documents",
            "md5": "abc123def456abc1",
            "size": -1,
            "mountId": null,
            "type": 1,
            "isMount": false,
            "parent": null,
            "suffix": null
        },
        {
            "id": "1054223881344122881",
            "uid": 1054223881344122880,
            "createAt": "2024-05-07T08:30:00.000+00:00",
            "updateAt": "2024-05-07T08:30:00.000+00:00",
            "node": "abc123def456abc1",
            "mtime": 1715050000000,
            "ctime": 1715050000000,
            "name": "photo.jpg",
            "md5": "d41d8cd98f00b204e9800998ecf8427e",
            "size": 204800,
            "mountId": null,
            "type": 2,
            "isMount": false,
            "parent": null,
            "suffix": "jpg"
        }
    ]
}
```

**字段说明**:

| 字段       | 类型    | 说明                                       |
|------------|---------|--------------------------------------------|
| `id`       | string  | 文件记录 ID（雪花算法生成）                |
| `uid`      | number  | 文件所属用户 ID                            |
| `createAt` | string  | 记录创建时间                               |
| `updateAt` | string  | 记录最后更新时间                           |
| `node`     | string  | 文件所在目录的节点 ID                      |
| `mtime`    | number  | 文件修改时间（Unix 毫秒时间戳）            |
| `ctime`    | number  | 文件创建时间（Unix 毫秒时间戳）            |
| `name`     | string  | 文件或目录名称                             |
| `md5`      | string  | 文件 MD5；目录时为该目录的节点 ID          |
| `size`     | number  | 文件字节大小；目录固定为 `-1`              |
| `mountId`  | number  | 挂载点 ID，非挂载目录为 `null`             |
| `type`     | number  | 类型：`1` = 目录，`2` = 文件               |
| `isMount`  | boolean | 是否为外部挂载的文件系统节点               |
| `parent`   | string  | 上级目录名称（前端辅助字段，一般为 `null`）|
| `suffix`   | string  | 文件后缀名（不含点），目录为 `null`        |

**说明**: 返回的 `data` 为单层列表，默认按"目录在前、文件在后"合并返回。
