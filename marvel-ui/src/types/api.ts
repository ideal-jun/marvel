/** 后端统一返回体 */
export interface ApiResult<T = unknown> {
  code: number
  msg: string
  data: T
}

/** MyBatis-Plus 分页返回体 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 登录请求体 */
export interface LoginBody {
  username: string
  password: string
  code: string
  uuid: string
}

/** 验证码 */
export interface CaptchaVO {
  uuid: string
  img: string
}

/** 登录结果 */
export interface LoginResult {
  token: string
}

/** 当前用户信息 */
export interface SysUserVO {
  id: number
  username: string
  nickname: string
  avatar: string | null
  deptId: number | null
  deptName: string | null
  email: string
  phone: string
  status: string
}

export interface UserInfoResult {
  user: SysUserVO
  roles: string[]
  permissions: string[]
}

/** 菜单/路由节点（后端 MenuDTO） */
export interface MenuNode {
  id: number
  parentId: number
  menuName: string
  /** M=目录 C=菜单 F=按钮 */
  menuType: 'M' | 'C' | 'F'
  path: string
  component: string
  perms: string
  icon: string
  orderNum: number
  visible: string
  status: string
  children: MenuNode[] | null
}

/** 用户实体（列表/详情） */
export interface SysUserRow {
  userId: number
  deptId: number | null
  username: string
  nickname: string
  email: string
  phone: string
  sex: string
  status: string
  remark: string | null
  createTime: string
  updateTime: string | null
  deptName: string | null
}

/** 角色实体 */
export interface SysRoleRow {
  roleId: number
  roleName: string
  roleKey: string
  roleSort: number
  dataScope: string
  status: string
  remark: string | null
  createTime: string
}

/** 角色下拉选项 */
export interface SysRoleOption {
  roleId: number
  roleName: string
}

/** 部门实体 */
export interface SysDeptRow {
  deptId: number
  parentId: number
  ancestors: string
  deptName: string
  orderNum: number
  leader: string | null
  phone: string | null
  status: string
  createTime: string
}

/** 菜单实体（管理页平铺列表） */
export interface SysMenuRow {
  menuId: number
  parentId: number
  menuName: string
  menuType: 'M' | 'C' | 'F'
  orderNum: number
  path: string
  component: string | null
  perms: string | null
  icon: string
  visible: string
  status: string
  createTime: string
  /** 树形接口返回时存在 */
  children?: SysMenuRow[] | null
  /** 懒加载接口返回：是否存在子菜单 */
  hasChild?: boolean | null
}

/** 用户详情响应 */
export interface UserDetailResult {
  user: SysUserRow
  roleIds: number[]
}

/* ==================== 二期：字典/参数/公告/任务 ==================== */

/** 字典类型 */
export interface SysDictTypeRow {
  dictId: number
  dictName: string
  dictType: string
  status: string
  remark: string | null
  createTime: string
}

/** 字典数据 */
export interface SysDictDataRow {
  dictCode: number
  dictType: string
  dictLabel: string
  dictValue: string
  orderNum: number
  status: string
  remark: string | null
  createTime: string
}

/** 系统参数 */
export interface SysConfigRow {
  configId: number
  configName: string
  configKey: string
  configValue: string
  remark: string | null
  createTime: string
}

/** 通知公告 */
export interface SysNoticeRow {
  noticeId: number
  title: string
  content: string | null
  /** 1=通知 2=公告 */
  type: string
  status: string
  createTime: string
}

/** 我的消息：已发布公告 + 当前用户的已读标记（站内消息面板与「我的消息」页共用） */
export interface MyNoticeRow {
  noticeId: number
  title: string
  content: string | null
  /** 1=通知 2=公告 */
  type: string
  createTime: string
  /** 当前用户是否已读 */
  read: boolean
}

/** 定时任务 */
export interface SysJobRow {
  jobId: number
  jobName: string
  jobGroup: string
  /** beanName.method */
  invokeTarget: string
  cronExpression: string
  status: string
  remark: string | null
  createTime: string
}

/** 任务执行日志 */
export interface SysJobLogRow {
  jobLogId: number
  jobId: number
  jobName: string
  status: string
  errorMsg: string | null
  startTime: string
  endTime: string
}

/** 操作日志（sys_oper_log） */
export interface SysOperLogRow {
  operId: number
  title: string
  /** 业务类型 + 方法全名，如 "UPDATE com.marvel...update" */
  method: string
  requestMethod: string
  operUrl: string
  operParam: string | null
  operUser: string
  operIp: string
  status: string
  errorMsg: string | null
  operTime: string | null
}

/** 登录日志（sys_logininfor） */
export interface SysLogininforRow {
  infoId: number
  username: string
  ipaddr: string
  browser: string
  os: string
  msg: string
  status: string
  loginTime: string | null
}

/* ==================== 首页看板 ==================== */

/** 看板概览统计 */
export interface DashboardStats {
  userCount: number
  enabledUserCount: number
  roleCount: number
  deptCount: number
  noticeCount: number
  onlineCount: number
  todayLoginCount: number
  todayOperCount: number
}

/** 登录趋势点（按日） */
export interface LoginTrendPoint {
  date: string
  success: number
  fail: number
}

/** 操作趋势点（按日） */
export interface OperTrendPoint {
  date: string
  count: number
}

/** 通用名称-数值对（饼图/柱图） */
export interface NameValue {
  name: string
  value: number
}

/** 任务执行统计 */
export interface JobStats {
  total: number
  enabled: number
  success: number
  fail: number
}

/* ==================== 个人中心 ==================== */

/** 当前用户资料（后端 SysUser，密码密文已抹除） */
export interface ProfileInfo {
  userId: number
  deptId: number | null
  username: string
  nickname: string
  email: string | null
  phone: string | null
  sex: string | null
  avatar: string | null
  status: string
  remark?: string | null
  createTime?: string
  deptName?: string | null
}

/** 在线用户（Sa-Token 活跃会话，按用户去重） */
export interface OnlineRow {
  userId: number
  username: string | null
  loginIp: string | null
  /** 登录时间（epoch 毫秒） */
  loginTime: number | null
  userAgent: string | null
  /** 令牌剩余有效期（秒） */
  tokenTimeout: number
}
