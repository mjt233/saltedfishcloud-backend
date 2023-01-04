ALTER TABLE desktop_component_config ADD use_card int COMMENT '是否使用卡片样式';
UPDATE desktop_component_config SET use_card = 1 WHERE use_card IS NULL;