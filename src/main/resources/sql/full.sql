-- MySQL dump 10.13  Distrib 8.0.25, for Linux (x86_64)
--
-- Host: 127.0.0.1    Database: xyy
-- ------------------------------------------------------
-- Server version       8.0.23

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `config`
--

DROP TABLE IF EXISTS `config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config` (
                          `key` varchar(64) NOT NULL,
                          `value` varchar(512) NOT NULL,
                          PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `download_task`
--

DROP TABLE IF EXISTS `download_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `download_task` (
                                 `id` varchar(128) NOT NULL,
                                 `uid` int unsigned DEFAULT NULL,
                                 `url` varchar(2048) DEFAULT NULL,
                                 `proxy` varchar(128) DEFAULT NULL,
                                 `state` varchar(128) DEFAULT 'waiting',
                                 `name` varchar(1024) DEFAULT NULL,
                                 `size` bigint DEFAULT NULL,
                                 `message` text,
                                 `created_at` datetime DEFAULT NULL,
                                 `finish_at` datetime DEFAULT NULL,
                                 `save_path` varchar(2048) DEFAULT NULL,
                                 `created_by` int unsigned DEFAULT NULL,
                                 `loaded` bigint unsigned DEFAULT '0',
                                 PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file_table`
--

DROP TABLE IF EXISTS `file_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_table` (
                              `uid` int unsigned NOT NULL,
                              `name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
                              `node` char(32) DEFAULT NULL,
                              `size` bigint NOT NULL,
                              `md5` char(32) DEFAULT NULL,
                              `created_at` timestamp NULL DEFAULT NULL,
                              `updated_at` timestamp NULL DEFAULT NULL,
                              UNIQUE KEY `file_index` (`node`,`name`,`uid`),
                              KEY `md5_index` (`md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `node_list`
--

DROP TABLE IF EXISTS `node_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `node_list` (
                             `name` varchar(512) DEFAULT NULL,
                             `id` char(32) DEFAULT NULL,
                             `parent` char(32) DEFAULT NULL,
                             `uid` int unsigned DEFAULT NULL,
                             UNIQUE KEY `node_name_index` (`parent`,`name`),
                             KEY `id_index` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `proxy`
--

DROP TABLE IF EXISTS `proxy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `proxy` (
                         `name` varchar(128) NOT NULL,
                         `type` varchar(16) NOT NULL,
                         `address` varchar(512) NOT NULL,
                         `port` smallint unsigned NOT NULL,
                         PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
                        `id` int unsigned NOT NULL AUTO_INCREMENT,
                        `user` varchar(32) DEFAULT NULL,
                        `pwd` varchar(32) DEFAULT NULL,
                        `last_login` int unsigned DEFAULT NULL,
                        `type` int unsigned DEFAULT '0',
                        `role` varchar(32) DEFAULT NULL,
                        `quota` int unsigned DEFAULT '10',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `user_index` (`user`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
CREATE TABLE `collection` (
                              `id` CHAR(32) PRIMARY KEY COMMENT '收集任务ID',
                              `uid` INT UNSIGNED NOT NULL COMMENT '创建者ID',
                              `nickname` VARCHAR(128) NOT NULL COMMENT '接收者署名',
                              `describe` TEXT COMMENT '收集任务描述',
                              `title` VARCHAR(128) NOT NULL COMMENT '标题',
                              `max_size` BIGINT NOT NULL COMMENT '允许的文件最大大小（Byte），-1为无限制',
                              `allow_anonymous` BOOLEAN NOT NULL COMMENT '是否允许匿名上传',
                              `allow_max` INT NOT NULL COMMENT '允许的最大收集文件数量，-1为无限制',
                              `pattern` VARCHAR(1024) COMMENT '文件名匹配表达式，可以是正则或字段拼接',
                              `field` VARCHAR(1024) COMMENT 'JSON类型数组，每个元素应包含name - 字段名称，pattern - 匹配正则，describe - 字段描述，type - 类型',
                              `save_node` CHAR(32) NOT NULL COMMENT '收集到文件后保存到的网盘数据节点',
                              `expired_at` DATETIME NOT NULL COMMENT '收集任务过期时间',
                              `created_at` DATETIME NOT NULL DEFAULT NOW() COMMENT '收集任务创建日期',
                              `state` ENUM('OPEN', 'CLOSED') NOT NULL COMMENT '状态，开放或关闭'
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX uid_index ON collection(uid);
ALTER TABLE node_list ADD `collecting` BOOLEAN COMMENT '该节点是否处于收集文件中';
ALTER TABLE node_list ADD `sharing` BOOLEAN COMMENT '该节点是否处于分享状态';

-- Dump completed on 2021-10-31 17:03:31
