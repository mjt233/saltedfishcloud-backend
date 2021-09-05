CREATE TABLE IF NOT EXISTS proxy (
    `name` VARCHAR(128) NOT NULL PRIMARY KEY ,
    `type` VARCHAR(16) NOT NULL ,
    `address` VARCHAR(512) NOT NULL ,
    `port` SMALLINT UNSIGNED NOT NULL
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS  download_task (
    `id` CHAR(32) PRIMARY KEY,
    `uid` INT UNSIGNED,
    `url` VARCHAR(2048),
    `proxy` VARCHAR(128) default NULL,
    `state` VARCHAR(128) default 'waiting'
);
