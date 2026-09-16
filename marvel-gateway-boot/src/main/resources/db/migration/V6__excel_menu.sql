-- 用户导入/导出按钮权限
INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(1005, 100, '用户导出', 'F', 6, NULL, NULL, 'system:user:export', '#', '0', '0'),
(1006, 100, '用户导入', 'F', 7, NULL, NULL, 'system:user:import', '#', '0', '0');
