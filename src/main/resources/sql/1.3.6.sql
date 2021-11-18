CREATE TABLE share (
    `id` INT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '分享ID',
    `verification` CHAR(32) NOT NULL COMMENT '分享校验码',
    `uid` INT UNSIGNED NOT NULL COMMENT '分享者ID',
    `nid` CHAR(32) NOT NULL COMMENT '资源ID，分享目录则为目录本身的节点ID，文件则为文件MD5',
    `parent_id` CHAR(32) NOT NULL COMMENT '资源所处目录的节点ID',
    `type` ENUM('FILE', 'DIR') NOT NULL COMMENT '分享类型，可为文件或目录',
    `size` BIGINT NOT NULL COMMENT '文件大小，目录时为-1',
    `extract_code` VARCHAR(16) COMMENT '资源提取码，为null则表示不需要提取码',
    `name` VARCHAR(1024) NOT NULL COMMENT '分享的资源名称，即为文件名或目录名',
    `created_at` DATETIME NOT NULL COMMENT '分享创建日期',
    `expired_at` DATETIME COMMENT '分享过期日期',
    INDEX uid_index(uid)
)ENGINE = InnoDB DEFAULT CHARSET=utf8mb4;
