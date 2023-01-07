ALTER TABLE desktop_component_config ADD enabled int COMMENT '是否启用';
UPDATE desktop_component_config SET enabled = 1 WHERE enabled IS NULL;
