-- =============================================================
-- 性能索引补充（针对树查询、字典、用户部门过滤、日志过滤等高频过滤列）
-- 说明：V1 仅为主键/唯一键与时间列建了索引，这里补齐其余高频查询列。
-- =============================================================

-- 菜单树：listLevel / collectChildren(旧) / fillHasChild 按 parent_id 过滤
CREATE INDEX idx_menu_parent ON sys_menu (parent_id);

-- 部门树：createDept/deleteDept 统计子部门、按父部门查询
CREATE INDEX idx_dept_parent ON sys_dept (parent_id);

-- 用户按部门过滤（pageUsers 的子查询外键）
CREATE INDEX idx_user_dept ON sys_user (dept_id);

-- 字典数据按类型查询（最常用的下拉数据来源）
CREATE INDEX idx_dict_data_type ON sys_dict_data (dict_type);

-- 角色-菜单反查：SysRoleMapper.selectRolesByMenuId 按 menu_id 查
CREATE INDEX idx_role_menu_menu ON sys_role_menu (menu_id);

-- 用户-角色反查：按 role_id 查拥有该角色的用户（角色删除/统计场景）
CREATE INDEX idx_user_role_role ON sys_user_role (role_id);

-- 任务执行日志按任务查询
CREATE INDEX idx_job_log_job ON sys_job_log (job_id);

-- 登录日志常用过滤列
CREATE INDEX idx_logininfor_username ON sys_logininfor (username);
CREATE INDEX idx_logininfor_ipaddr ON sys_logininfor (ipaddr);

-- 操作日志按操作人过滤（title/status 低基数列不再单独建索引，避免拖慢写入）
CREATE INDEX idx_operlog_user ON sys_oper_log (oper_user);
