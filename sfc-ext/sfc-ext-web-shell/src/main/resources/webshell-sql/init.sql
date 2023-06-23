CREATE TABLE shell_execute_record
(
    id        BIGINT       NOT NULL,
    uid       BIGINT       NULL,
    create_at datetime     NULL,
    update_at datetime     NULL,
    cmd       VARCHAR(800) NOT NULL,
    work_dir  VARCHAR(255) NULL,
    host      VARCHAR(255) NULL,
    CONSTRAINT pk_shellexecuterecord PRIMARY KEY (id)
);