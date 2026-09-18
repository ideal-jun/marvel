-- =============================================================
-- V11：个人中心菜单（配套「我的消息」页面）
-- =============================================================

-- 个人中心目录
INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(3, 0, '个人中心', 'M', 3, 'profile', NULL, NULL, 'mdi-account-circle-outline', '0', '0');

-- 我的消息：仅本人可见的消息列表，接口按登录用户过滤，无需按钮级权限
INSERT INTO sys_menu (menu_id, parent_id, menu_name, menu_type, order_num, path, component, perms, icon, visible, status) VALUES
(300, 3, '我的消息', 'C', 1, 'message', 'profile/message/index', NULL, 'mdi-bell-outline', '0', '0');

-- 普通角色同样可见（超级管理员默认拥有全部菜单）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 3), (2, 300);
