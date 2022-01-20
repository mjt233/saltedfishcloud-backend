DROP INDEX file_index ON file_table;
UPDATE file_table SET node = uid WHERE node = 'root';
CREATE UNIQUE INDEX file_index ON file_table(node, name, uid);

DROP INDEX node_name_index ON node_list;
UPDATE node_list SET parent = uid WHERE parent = 'root';
CREATE UNIQUE INDEX node_name_index ON node_list(parent, name);

