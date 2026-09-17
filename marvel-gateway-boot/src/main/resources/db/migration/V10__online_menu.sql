-- =============================================================
-- V10：在线用户菜单（配套 SysOnlineController 的菜单/权限种子）
-- =============================================================

-- 系统管理下的在线用户页面
INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(206, 1, '在线用户', 'C', 11, 'online', 'system/online/index', 'system:online:list', 'mdi-account-clock-outline', '0', '0');

-- 在线用户按钮
INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(2060, 206, '在线查询', 'F', 1, NULL, NULL, 'system:online:list',    '#', '0', '0'),
(2061, 206, '强制下线', 'F', 2, NULL, NULL, 'system:online:kickout', '#', '0', '0');
