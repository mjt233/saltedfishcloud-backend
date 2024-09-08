ALTER TABLE `mount_point` ADD COLUMN `is_proxy_store_record` BOOLEAN NULL COMMENT '委托存储记录';
ALTER TABLE `mount_point` ADD COLUMN `update_at` DATETIME COMMENT '更新日期';
ALTER TABLE `node_list` ADD COLUMN `is_mount` BOOLEAN NULL COMMENT '是否为挂载点目录节点';
ALTER TABLE `file_table` ADD COLUMN `is_mount` BOOLEAN NULL COMMENT '是否为挂载点目录节点';