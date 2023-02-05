UPDATE encode_convert_task SET task_id = null;
ALTER TABLE encode_convert_task MODIFY task_id bigint COMMENT '异步任务id';
ALTER TABLE encode_convert_task DROP task_status;