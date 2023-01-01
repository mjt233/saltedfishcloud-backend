CREATE TABLE encode_convert_task (
    id BIGINT PRIMARY KEY,
    uid BIGINT COMMENT '任务所属用户id',
    task_id CHAR(128) COMMENT '系统的异步任务id',
    task_status INT COMMENT '任务状态',
    type varchar(10) COMMENT '转换类型',
    params TEXT COMMENT '任务JSON参数',
    create_at DATETIME,
    update_at DATETIME
)ENGINE=InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频编码转换任务记录表';

CREATE INDEX idx_task_id ON encode_convert_task(task_id);