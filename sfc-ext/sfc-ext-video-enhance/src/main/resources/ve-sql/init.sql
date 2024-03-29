CREATE TABLE encode_convert_task (
                                    id BIGINT PRIMARY KEY,
                                    uid BIGINT COMMENT '任务所属用户id',
                                    task_id BIGINT COMMENT '系统的异步任务id',
                                    type varchar(10) COMMENT '转换类型',
                                    params TEXT COMMENT '任务JSON参数',
                                    create_at DATETIME,
                                    update_at DATETIME
)ENGINE=InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '视频编码转换任务记录表';

CREATE INDEX idx_task_id ON encode_convert_task(task_id);

CREATE TABLE encode_convert_task_log (
                                         id bigint primary key,
                                         task_id bigint COMMENT '任务id',
                                         task_log longtext COMMENT '任务日志',
                                         key idx_task_id(task_id)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '编码转换任务日志表';