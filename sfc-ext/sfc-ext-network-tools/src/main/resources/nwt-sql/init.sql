CREATE TABLE wol_device
(
    id           BIGINT       NOT NULL,
    uid          BIGINT       NULL,
    create_at    datetime     NULL,
    update_at    datetime     NULL,
    name         VARCHAR(255) NULL COMMENT '设备名称',
    mac          VARCHAR(255) NOT NULL COMMENT '设备MAC地址',
    ip           VARCHAR(255) NULL COMMENT '设备IP地址',
    port         INT          NULL COMMENT '端口',
    send_ip      VARCHAR(255) NULL COMMENT '魔术包发送IP或广播地址',
    last_wake_at datetime     NULL COMMENT '上次唤醒时间',
    show_order   INT          NULL COMMENT '显示顺序',
    CONSTRAINT pk_woldevice PRIMARY KEY (id)
) CHARSET=utf8mb4 COMMENT='网络唤醒设备';