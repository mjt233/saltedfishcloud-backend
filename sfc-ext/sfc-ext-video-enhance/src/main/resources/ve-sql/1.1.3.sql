CREATE TABLE encode_convert_task_log (
    id bigint primary key,
    task_id bigint COMMENT '任务id',
    task_log longtext COMMENT '任务日志',
    key idx_task_id(task_id)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '编码转换任务日志表';