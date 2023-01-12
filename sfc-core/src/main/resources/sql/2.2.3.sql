CREATE TABLE comment (
    id bigint primary key,
    topic_id bigint COMMENT '话题id，可以是任意表的，0为系统留言板' NOT NULL,
    reply_id bigint COMMENT '回复id',
    uid bigint COMMENT '发送人用户id',
    ip char(48) COMMENT '发送时的ip地址',
    content text COMMENT '评论的内容',
    create_at DATETIME,
    update_at DATETIME,
    is_delete int,
    key idx_topic(topic_id)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '评论表';

ALTER TABLE user CHANGE id id BIGINT;

