CREATE TABLE quick_share
(
    id         BIGINT       NOT NULL comment '主键',
    uid        BIGINT       NULL comment '创建人id',
    create_at  datetime     NULL comment '创建日期',
    update_at  datetime     NULL comment '更新日期',
    code       VARCHAR(255) NOT NULL comment '提取码',
    file_name  VARCHAR(255) NULL comment '文件名',
    size       BIGINT       NULL comment '文件大小',
    expired_at datetime     NULL comment '过期日期',
    message    VARCHAR(255) NULL comment '留言',
    CONSTRAINT pk_quickshare PRIMARY KEY (id),
    INDEX idx_expired(expired_at)
);