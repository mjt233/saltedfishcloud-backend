ALTER TABLE file_table MODIFY uid BIGINT;
ALTER TABLE file_table ADD id BIGINT;
ALTER TABLE file_table ADD ctime BIGINT COMMENT '文件创建日期';
ALTER TABLE file_table ADD mtime BIGINT COMMENT '文件修改日期';
ALTER TABLE file_table CHANGE created_at create_at DATETIME COMMENT '数据创建日期';
ALTER TABLE file_table CHANGE updated_at update_at DATETIME COMMENT '数据更新日期';
UPDATE file_table SET mtime = UNIX_TIMESTAMP(update_at)*1000 WHERE mtime IS NULL;
UPDATE file_table SET ctime = UNIX_TIMESTAMP(create_at)*1000 WHERE ctime IS NULL;
UPDATE file_table SET ctime = UNIX_TIMESTAMP(update_at)*1000 WHERE ctime IS NULL;


ALTER TABLE user MODIFY id BIGINT;
ALTER TABLE share MODIFY id BIGINT;
ALTER TABLE node_list MODIFY uid BIGINT;
ALTER TABLE collection_rec MODIFY uid BIGINT;
ALTER TABLE collection MODIFY uid BIGINT;
ALTER TABLE download_task MODIFY uid BIGINT;
ALTER TABLE download_task MODIFY created_by BIGINT;
