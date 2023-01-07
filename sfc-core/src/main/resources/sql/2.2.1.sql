# CREATE TABLE desktop_component (
#     id bigint primary key,
#     uid bigint COMMENT '创建人uid',
#     title VARCHAR(50) COMMENT '组件标题',
#     name VARCHAR(50) COMMENT '组件name',
#     config TEXT DEFAULT '[]' COMMENT '组件配置项JSON',
#     type VARCHAR(50) DEFAULT 'vue' COMMENT '组件类型',
#     create_at DATETIME,
#     update_at DATETIME
# )ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '桌面小组件定义表';

CREATE TABLE desktop_component_config (
   id bigint primary key,
   uid bigint COMMENT '使用的目标uid',
   title VARCHAR(50) COMMENT '组件标题',
   name VARCHAR(50) COMMENT '组件name',
   params TEXT COMMENT '给组件设定的配置参数json',
   type VARCHAR(50) DEFAULT 'vue' COMMENT '组件类型',
   show_order int COMMENT '显示顺序，越小越靠前',
   width int COMMENT '布局占用的单位宽度',
   height int COMMENT '布局占用的单位高度',
   remark text COMMENT '备注',
   create_at DATETIME,
   update_at DATETIME,
   key idx_name(name),
   key idx_uid(uid)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '桌面小组件使用配置表';