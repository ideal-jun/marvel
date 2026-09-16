-- 文件管理菜单与权限
INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(220, 2, '文件管理', 'C', 2, 'file', 'infra/file/index', 'infra:file:list', 'mdi-folder-multiple-outline', '0', '0');

INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(2200, 220, '文件查询', 'F', 1, NULL, NULL, 'infra:file:list',     '#', '0', '0'),
(2201, 220, '文件下载', 'F', 2, NULL, NULL, 'infra:file:download', '#', '0', '0'),
(2202, 220, '文件删除', 'F', 3, NULL, NULL, 'infra:file:remove',   '#', '0', '0'),
(2203, 220, '文件上传', 'F', 4, NULL, NULL, 'infra:file:upload',   '#', '0', '0');
