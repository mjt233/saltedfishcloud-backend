CREATE TABLE IF NOT EXISTS proxy (
    `name` VARCHAR(128) NOT NULL PRIMARY KEY ,
    `type` VARCHAR(16) NOT NULL ,
    `address` VARCHAR(512) NOT NULL ,
    `port` SMALLINT UNSIGNED NOT NULL
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `download_task` (
    `id` varchar(128) NOT NULL,
    `uid` int unsigned DEFAULT NULL,
    `url` varchar(2048) DEFAULT NULL,
    `proxy` varchar(128) DEFAULT NULL,
    `state` varchar(128) DEFAULT 'waiting',
    `name` VARCHAR(1024),
    `size` bigint unsigned,
    `message` text,
    `created_at` datetime DEFAULT NULL,
    `finish_at` datetime DEFAULT NULL,
    `save_path` varchar(2048) DEFAULT NULL,
    `created_by` int unsigned DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
