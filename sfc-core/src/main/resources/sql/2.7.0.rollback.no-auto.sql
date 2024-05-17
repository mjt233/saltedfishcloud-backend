ALTER TABLE file_table DROP id;
ALTER TABLE file_table MODIFY uid int;
ALTER TABLE file_table DROP ctime;
ALTER TABLE file_table DROP mtime;
ALTER TABLE file_table CHANGE create_at created_at DATETIME COMMENT '数据创建日期';
ALTER TABLE file_table CHANGE update_at updated_at DATETIME COMMENT '数据更新日期';


ALTER TABLE user MODIFY id int;
ALTER TABLE share MODIFY id int;
ALTER TABLE node_list MODIFY uid int;
ALTER TABLE collection_rec MODIFY uid int;
ALTER TABLE collection MODIFY uid int;
ALTER TABLE download_task MODIFY uid int;
ALTER TABLE download_task MODIFY created_by int;
