CREATE TABLE schedule_job_record
(
    id                BIGINT       NOT NULL,
    uid               BIGINT       NULL,
    create_at         datetime     NULL,
    update_at         datetime     NULL,
    job_name          VARCHAR(255) NOT NULL COMMENT '任务名称',
    job_describe      VARCHAR(255) NULL COMMENT '任务描述',
    last_execute_date BIGINT     NULL COMMENT '上次执行日期',
    CONSTRAINT pk_schedulejobrecord PRIMARY KEY (id),
    UNIQUE KEY idx_job_name(job_name)
)COMMENT '定时任务记录' CHARSET=utf8mb4;