CREATE TABLE async_task_record
(
    id           BIGINT       NOT NULL,
    uid          BIGINT       NULL,
    create_at    datetime     NULL,
    update_at    datetime     NULL,
    task_type    VARCHAR(255) NULL COMMENT '任务类型',
    name         VARCHAR(255) NULL COMMENT '任务名称',
    params       longtext     NULL COMMENT '任务参数',
    execute_date datetime     NULL COMMENT '执行日期',
    finish_date  datetime     NULL COMMENT '完成日期',
    failed_date  datetime     NULL COMMENT '失败日期',
    executor     VARCHAR(255) NULL COMMENT '执行器',
    status       INT          NULL COMMENT '状态',
    cpu_overhead INT          NULL COMMENT 'CPU开销',
    CONSTRAINT pk_asynctaskrecord PRIMARY KEY (id)
)CHARSET = utf8mb4 COMMENT = '异步任务记录';

CREATE TABLE async_task_log_record
(
    id        BIGINT       NOT NULL,
    uid       BIGINT       NULL,
    create_at datetime     NULL,
    update_at datetime     NULL,
    task_id   BIGINT       NULL,
    log_info  longtext     NULL,
    CONSTRAINT pk_asynctasklogrecord PRIMARY KEY (id),
    key idx_task_id(task_id)
)CHARSET = utf8mb4 COMMENT = '异步任务日志记录';