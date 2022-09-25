CREATE TABLE mount_point (
    id BIGINT NOT NULL PRIMARY KEY,
    uid BIGINT NOT NULL COMMENT '用户id',
    nid CHAR(32) COMMENT '挂载的节点id',
    protocol VARCHAR(32) COMMENT '挂载的文件系统协议',
    params TEXT COMMENT '挂载参数',
    create_at DATETIME COMMENT '创建日期',
    KEY `idx_uid`(uid, nid)
)ENGINE=InnoDB CHARSET=utf8mb4 COMMENT '第三方文件系统挂载点';