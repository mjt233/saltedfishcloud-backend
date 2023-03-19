CREATE TABLE user_custom_attribute
(
    id        BIGINT       NOT NULL,
    uid       BIGINT       NULL,
    create_at datetime     NULL,
    update_at datetime     NULL,
    label     VARCHAR(255) NULL COMMENT '标签',
    remark    VARCHAR(255) NULL COMMENT '备注',
    json      longtext NULL COMMENT '主要内容json',
    CONSTRAINT pk_usercustomattribute PRIMARY KEY (id)
)CHARSET=utf8mb4 COMMENT '用户自定义属性';