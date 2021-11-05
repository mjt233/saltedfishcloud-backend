CREATE TABLE `collection` (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '收集任务ID',
    `verification` CHAR(32) NOT NULL COMMENT '校验码',
    `uid` INT UNSIGNED NOT NULL COMMENT '创建者ID',
    `nickname` VARCHAR(128) NOT NULL COMMENT '接收者署名',
    `describe` TEXT COMMENT '收集任务描述',
    `title` VARCHAR(128) NOT NULL COMMENT '标题',
    `max_size` BIGINT NOT NULL COMMENT '允许的文件最大大小（Byte），-1为无限制',
    `allow_anonymous` BOOLEAN NOT NULL COMMENT '是否允许匿名上传',
    `allow_max` INT NOT NULL COMMENT '允许的最大收集文件数量，-1为无限制',
    `available` INT NOT NULL COMMENT '该收集可用容量（还可以接受的文件数）',
    `pattern` VARCHAR(1024) COMMENT '文件名匹配表达式，可以是正则或字段拼接',
    `ext_pattern` VARCHAR(1024) COMMENT '允许的文件后缀名正则表达式，被测试的后缀名不带.',
    `field` VARCHAR(1024) COMMENT 'JSON类型数组，每个元素应包含name - 字段名称，pattern - 匹配正则，describe - 字段描述，type - 类型',
    `save_node` CHAR(32) NOT NULL COMMENT '收集到文件后保存到的网盘数据节点',
    `expired_at` DATETIME NOT NULL COMMENT '收集任务过期时间',
    `created_at` DATETIME NOT NULL DEFAULT NOW() COMMENT '收集任务创建日期',
    `state` ENUM('OPEN', 'CLOSED') NOT NULL COMMENT '状态，开放或关闭',
    INDEX uid_index(uid)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE node_list ADD `collecting` BOOLEAN COMMENT '该节点是否处于收集文件中';
ALTER TABLE node_list ADD `sharing` BOOLEAN COMMENT '该节点是否处于分享状态';

CREATE TABLE collection_rec (
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `cid` BIGINT NOT NULL COMMENT '收集任务ID',
    `uid` INT UNSIGNED NOT NULL COMMENT '上传者ID，匿名用户为0',
    `filename` VARCHAR(1024) NOT NULL COMMENT '文件名',
    `size` BIGINT NOT NULL COMMENT '文件大小',
    `md5` CHAR(32) NOT NULL COMMENT '文件MD5校验码',
    `created_at` DATETIME NOT NULL COMMENT '文件上传日期',
    INDEX cid_index(cid)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
