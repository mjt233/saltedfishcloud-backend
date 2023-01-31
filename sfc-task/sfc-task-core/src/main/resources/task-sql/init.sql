CREATE TABLE async_task_record
(
    id           BIGINT       NOT NULL,
    uid          BIGINT       NULL,
    create_at    datetime     NULL,
    update_at    datetime     NULL,
    task_type    VARCHAR(255) NULL COMMENT '任务类型',
    name         VARCHAR(255) NULL COMMENT '任务名称',
    params       VARCHAR(255) NULL COMMENT '任务参数',
    execute_date datetime     NULL COMMENT '执行日期',
    finish_date  datetime     NULL COMMENT '完成日期',
    failed_date  datetime     NULL COMMENT '失败日期',
    executor     VARCHAR(255) NULL COMMENT '执行器',
    status       INT          NULL COMMENT '状态',
    cpu_overhead INT          NULL COMMENT 'CPU开销',
    CONSTRAINT pk_asynctaskrecord PRIMARY KEY (id)
)ENGINE=InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '异步任务记录';