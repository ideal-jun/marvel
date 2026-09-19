/**
 * Vuetify 校验规则插件（createRulesPlugin）：把项目策略注册成「规则别名」，
 * 页面里声明式引用；通用别名的消息自动走 Vuetify i18n（项目已配 zhHans）。
 *
 * 用法——别名是「构建器」，字符串形式不带参数、数组形式 ['名字', 参数...]：
 *   :rules="['$required']"                            内置：必填
 *   :rules="[['$maxLength', 50]]"                     内置：长度上限
 *   :rules="['phone']" / ['email'] / ['password']      本项目：与后端策略类对齐
 *   :rules="[['requiredWhen', () => cond, '提示语']]"  本项目：条件必填
 *
 * 注意：这里重新注册了 email —— 内置的 email 正则（^.+@\S+\.\S+$）过松，
 * 与后端 ContactPolicy 不一致，会被后端打回。
 */

/** 中国大陆手机号：1 开头、第二位 3-9、共 11 位（对齐 marvel-common ContactPolicy） */
const PHONE_RE = /^1[3-9]\d{9}$/
/** 邮箱：本地部分允许常见字符，域名可多级，顶级域 2-63 个字母（对齐 ContactPolicy） */
const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,63}$/
/** 邮箱长度上限取 sys_user.email 列宽（后端 ContactPolicy 允许 64，列宽 50，取更严的） */
const EMAIL_MAX_LENGTH = 50

const PASSWORD_MIN_LENGTH = 8
const PASSWORD_MAX_LENGTH = 32

type RuleResult = true | string

/** 必填（空白串视为未填；数字 0 视为已填，与内置 $required 行为一致） */
function checkRequired(value: unknown): RuleResult {
  const text = typeof value === 'string' ? value.trim() : value
  return (text !== null && text !== undefined && text !== '') || '不能为空'
}

/** 手机号格式（空值放行，是否必填交给 $required） */
function checkPhone(value: unknown): RuleResult {
  const text = String(value ?? '').trim()
  if (!text) return true
  return PHONE_RE.test(text) || '手机号格式不正确'
}

/** 邮箱格式与长度（空值放行） */
function checkEmail(value: unknown): RuleResult {
  const text = String(value ?? '').trim()
  if (!text) return true
  if (text.length > EMAIL_MAX_LENGTH) return `邮箱长度不能超过 ${EMAIL_MAX_LENGTH} 位`
  return EMAIL_RE.test(text) || '邮箱格式不正确'
}

/** 密码复杂度（对齐后端 PasswordPolicy：8-32 位、不含空白、同时含字母与数字） */
function checkPassword(value: unknown): RuleResult {
  const text = String(value ?? '')
  if (!text) return '密码不能为空'
  if (text.length < PASSWORD_MIN_LENGTH || text.length > PASSWORD_MAX_LENGTH) {
    return `密码长度需为 ${PASSWORD_MIN_LENGTH}-${PASSWORD_MAX_LENGTH} 位`
  }
  if (/\s/.test(text)) return '密码不能包含空白字符'
  if (!/[A-Za-z]/.test(text) || !/\d/.test(text)) return '密码必须同时包含字母和数字'
  return true
}

/** 构建器统一形态：可传 err 覆盖默认提示 */
function builder(check: (value: unknown) => RuleResult) {
  return (err?: string) =>
    (value: unknown): RuleResult => {
      const result = check(value)
      return result === true || err || result
    }
}

/**
 * 注册给 createRulesPlugin 的别名。
 * 覆盖内置 email；新增 phone / password / requiredWhen。
 */
export const rulesOptions = {
  aliases: {
    email: builder(checkEmail),
    phone: builder(checkPhone),
    password: builder(checkPassword),
    /** 条件必填：仅当 enabled() 为真时要求非空（模板里传闭包，校验时取当前值） */
    requiredWhen:
      (enabled: () => boolean, err?: string) =>
      (value: unknown): RuleResult => {
        if (!enabled()) return true
        const result = checkRequired(value)
        return result === true || err || result
      },
  },
}
