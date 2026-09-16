-- 服务监控菜单与权限
INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(205, 1, '服务监控', 'C', 10, 'monitor', 'system/monitor/index', 'system:monitor:list', 'mdi-server-network', '0', '0');

INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(2050, 205, '监控查询', 'F', 1, NULL, NULL, 'system:monitor:list',  '#', '0', '0'),
(2051, 205, 'Redis 信息', 'F', 2, NULL, NULL, 'system:monitor:redis', '#', '0', '0');
