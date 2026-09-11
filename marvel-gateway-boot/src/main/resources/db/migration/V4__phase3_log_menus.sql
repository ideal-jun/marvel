-- =============================================================
-- V4：三期日志审计菜单（操作日志/登录日志）
-- =============================================================

-- 系统管理下的日志菜单
INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(203, 1, '操作日志', 'C', 8, 'operlog', 'system/operlog/index', 'system:operlog:list', 'mdi-file-document-edit-outline', '0', '0'),
(204, 1, '登录日志', 'C', 9, 'logininfor', 'system/logininfor/index', 'system:logininfor:list', 'mdi-login-variant', '0', '0');

-- 操作日志按钮
INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(2030, 203, '日志查询', 'F', 1, NULL, NULL, 'system:operlog:list',   '#', '0', '0'),
(2031, 203, '日志删除', 'F', 2, NULL, NULL, 'system:operlog:remove', '#', '0', '0'),
(2032, 203, '日志清空', 'F', 3, NULL, NULL, 'system:operlog:clean',  '#', '0', '0');

-- 登录日志按钮
INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(2040, 204, '日志查询', 'F', 1, NULL, NULL, 'system:logininfor:list',   '#', '0', '0'),
(2041, 204, '日志删除', 'F', 2, NULL, NULL, 'system:logininfor:remove', '#', '0', '0'),
(2042, 204, '日志清空', 'F', 3, NULL, NULL, 'system:logininfor:clean',  '#', '0', '0');
