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
-- Table structure for table `collection`
--

DROP TABLE IF EXISTS `collection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collection` (
                              `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '收集任务ID',
                              `verification` char(32) NOT NULL COMMENT '校验码',
                              `uid` int unsigned NOT NULL COMMENT '创建者ID',
                              `nickname` varchar(128) NOT NULL COMMENT '接收者署名',
                              `describe` text COMMENT '收集任务描述',
                              `title` varchar(128) NOT NULL COMMENT '标题',
                              `max_size` bigint NOT NULL COMMENT '允许的文件最大大小（Byte），-1为无限制',
                              `allow_anonymous` tinyint(1) NOT NULL COMMENT '是否允许匿名上传',
                              `allow_max` int NOT NULL COMMENT '允许的最大收集文件数量，-1为无限制',
                              `available` int NOT NULL COMMENT '该收集可用容量（还可以接受的文件数）',
                              `pattern` varchar(1024) DEFAULT NULL COMMENT '文件名匹配表达式，可以是正则或字段拼接',
                              `ext_pattern` varchar(1024) DEFAULT NULL COMMENT '允许的文件后缀名正则表达式，被测试的后缀名不带.',
                              `field` varchar(1024) DEFAULT NULL COMMENT 'JSON类型数组，每个元素应包含name - 字段名称，pattern - 匹配正则，describe - 字段描述，type - 类型',
                              `save_node` char(32) NOT NULL COMMENT '收集到文件后保存到的网盘数据节点',
                              `save_path_snapshot` varchar(1024) NOT NULL COMMENT '收集到文件后保存到的网盘位置快照（仅记录创建时的设定）',
                              `expired_at` datetime NOT NULL COMMENT '收集任务过期时间',
                              `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收集任务创建日期',
                              `state` enum('OPEN','CLOSED') NOT NULL COMMENT '状态，开放或关闭',
                              PRIMARY KEY (`id`),
                              KEY `uid_index` (`uid`),
                              KEY `expired_index` (`expired_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `collection_rec`
--

DROP TABLE IF EXISTS `collection_rec`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collection_rec` (
                                  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '记录ID',
                                  `cid` bigint NOT NULL COMMENT '收集任务ID',
                                  `uid` int unsigned NOT NULL COMMENT '上传者ID，匿名用户为0',
                                  `filename` varchar(1024) NOT NULL COMMENT '文件名',
                                  `size` bigint NOT NULL COMMENT '文件大小',
                                  `md5` char(32) NOT NULL COMMENT '文件MD5校验码',
                                  `created_at` datetime NOT NULL COMMENT '文件上传日期',
                                  `ip` char(16) NOT NULL COMMENT '上传时的IP地址',
                                  PRIMARY KEY (`id`),
                                  KEY `cid_index` (`cid`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

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
                              KEY `md5_index` (`md5`),
                              KEY `uid_index` (`uid`)
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
                             `collecting` tinyint(1) DEFAULT NULL COMMENT '该节点是否处于收集文件中',
                             `sharing` tinyint(1) DEFAULT NULL COMMENT '该节点是否处于分享状态',
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
-- Table structure for table `share`
--

DROP TABLE IF EXISTS `share`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `share` (
                         `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '分享ID',
                         `verification` char(32) NOT NULL COMMENT '分享校验码',
                         `uid` int unsigned NOT NULL COMMENT '分享者ID',
                         `nid` char(32) NOT NULL COMMENT '资源ID，分享目录则为目录节点ID，文件则为文件MD5',
                         `parent_id` char(32) NOT NULL COMMENT '资源所处目录的节点ID',
                         `type` enum('FILE','DIR') NOT NULL COMMENT '分享类型，可为文件或目录',
                         `size` bigint NOT NULL COMMENT '文件大小，目录时为-1',
                         `extract_code` varchar(16) DEFAULT NULL COMMENT '资源提取码，为null则表示不需要提取码',
                         `name` varchar(1024) NOT NULL COMMENT '分享的资源名称，即为文件名或目录名',
                         `created_at` datetime NOT NULL COMMENT '分享创建日期',
                         `expired_at` datetime DEFAULT NULL COMMENT '分享过期日期',
                         PRIMARY KEY (`id`),
                         KEY `uid_index` (`uid`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
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
                        `email` varchar(256) NOT NULL,
                        `last_login` int unsigned DEFAULT NULL,
                        `type` int unsigned DEFAULT '0',
                        `role` varchar(32) DEFAULT NULL,
                        `quota` int unsigned DEFAULT '10',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `user_index` (`user`),
                        KEY `mail_index` (`email`)
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

-- Dump completed on 2021-12-25 16:16:14
