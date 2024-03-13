ALTER TABLE `share`
    CHANGE COLUMN `created_at` `create_at`  datetime NOT NULL COMMENT '创建日期' AFTER `name`,
    ADD COLUMN `update_at`  datetime NULL COMMENT '修改日期' AFTER `create_at`;