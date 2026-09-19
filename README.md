# Marvel 后台管理系统

Spring Boot 4 模块化单体后台管理系统，按微服务边界拆分模块，可平滑演进至 Spring Cloud。

## 技术栈

- 后端：Java 21 + Spring Boot 4.1 + Sa-Token 1.46 + MyBatis-Plus 3.5.17 + MySQL 8 + Redis + Flyway
- 前端：Vue 3 + Vuetify 4 + UnoCSS（官方 presetWind4 集成）+ TypeScript + Vite + Pinia + ECharts

## 前端样式方案（Vuetify 官方 presetWind4 集成）

参照官方指南与 `@vuetify/cli` 模板（`--css=unocss-wind4`）落地：

- **级联层**：`public/layers.css` 声明 `uno-*` 与 `vuetify-*` 层顺序，由 `index.html`
  最先加载；UnoCSS 产物经 `outputToCssLayers` 映射到 `uno-*` 层，与 Vuetify 组件样式互不冲突；
- **工具类**：`presetWind4`（TailwindCSS v4 命名，按需生成）+ `settings.scss` 关闭 Vuetify
  内置静态 utilities（`$utilities: false, $color-pack: false`）；
- **主题联动**：`uno.config.ts` 的 `theme.colors` 通过 `--v-theme-*` 变量映射 Vuetify 主题色，
  断点经 `src/theme/breakpoints.ts` 单一来源同时供给 Vuetify 与 UnoCSS；
- **Vuetify 命名兼容**：`text-h1..text-overline`（MD2 字号）、`rounded-*` 以 shortcuts 复刻；
  `elevation-*` 采用官网方案 A（对齐 TailwindCSS 阴影刻度 `--shadow-xs..2xl`，rules 内附
  Tailwind v4 标准值兜底——wind4 的 shadow token 按需输出，自定义 rule 的 var() 引用不会
  触发生成），`color` prop 动态类加入 safelist；
- **写法约定**：布局/间距/颜色用 wind4 工具类；组件行为优先组件 props（如 `v-list` 的
  `color`）；项目自定义样式写入 `@layer vuetify-overrides`（文件 `src/styles/overrides.css`，
  由 `main.ts` 引入），不覆盖组件库底层；
- **必备导入**：`src/plugins/vuetify.ts` 中 `import 'vuetify/styles'`（官方脚手架必备，
  提供 reset 与组件级 CSS 变量）；不要引入第三方 reset（unlayered 规则会压过 layer 内的
  组件样式）。 + Vue Router
- **实验室组件（labs）**：`vuetify/styles` 聚合产物**不含** labs 组件样式，用到时需要
  在 `src/plugins/vuetify.ts` 单独引入该组件的 CSS（如
  `import 'vuetify/lib/labs/VCommandPalette/VCommandPalette.css'`，文件自带
  `@layer vuetify-components`，与项目级联层架构一致）；组件本身按需
  `import { VXxx } from 'vuetify/labs/VXxx'`。当前已用：`VCommandPalette`（顶栏命令面板）。

## 前端表单约定

`v-model` 的空值必须区分控件类型，否则 Vuetify 会误判为「已选中」，把空状态下拉渲染成
已填充样式（标签浮到边框、框内空白）：

- **单选下拉**（`v-select` 等）无默认值时初始值绑定 `null`；不能是空串 `''` 或
  `undefined`——两者都会被 `VSelect` 内部 `wrapInArray` 包装成非空数组，
  使 `isDirty` 为真（源码 `VSelect.js` 的 `const isDirty = model.value.length > 0`）；
- **多选下拉**初始值绑定空数组 `[]`；
- **重置逻辑**与初始值保持一致：单选写回 `null`、多选写回 `[]`，对话框表单在
  `clearObject`（`delete` 字段）之后需对下拉字段显式赋 `null`；
- 文本输入框（`v-text-field`）用空串 `''` 或 `null` 均可，不受此限制。

## 前端列表操作区约定

列表卡的操作按钮统一放在 `ListPanel` 的 `#actions` 插槽，间距由容器统一提供：

- **间距**：`ListPanel` 标题行为 flex + `gap-2`（8px，与 Vuetify `v-card-actions` 的
  `gap: 0.5rem` 一致）；页面侧不要再写 `mr-*`/`ml-*`，否则与容器 gap 叠加；
- **图标按钮**（刷新、列设置）：`icon` + `variant="text"` + `density="comfortable"`，
  size 保持默认。图标按钮的方形尺寸 = `--v-btn-height` + density 偏移（default +12 /
  comfortable +0 / compact -8），默认 size 配 comfortable 才是 36×36，与默认尺寸文字
  按钮（36px 高）对齐；用 `size="small"` 会得到 40px 反而更高；
- **语义色**：主操作（新增/上传）`color="success"` 实心；危险操作（删除/清空）
  `variant="tonal"` + error/warning；次要操作（导出/导入）`variant="tonal"` + primary；
- 图标按钮一律配 `v-tooltip`，`location="bottom"`；
- **操作列固定在右侧**：所有表格的 `操作` 列在 headers 里声明 `fixed: 'end'`
  （Vuetify 支持 `boolean | 'start' | 'end'`，并要求该列有静态 `width`），
  横向滚动时列始终可见；用户页的 `ColumnSettings` 对 `'end'` 列禁用图钉切换。

## 对象存储（local / MinIO）

文件存储抽象为 `StorageService`，通过 `marvel.storage.type` 切换实现：

- `local`（默认）：本地磁盘，上传返回 `/uploads/yyyy/MM/dd/uuid.ext`；
- `minio`：对象存储，上传返回对象 key，`toUrl` 生成稳定直链（桶匿名只读）或预签名 URL；
  启动时自动建桶并按 `public-read` 开放匿名只读。

```bash
# 启动 MinIO
docker run -d --name minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
  -v marvel-minio-data:/data pgsty/minio:latest server /data --console-address ":9001"
# 以 MinIO 作为存储启动后端
STORAGE_TYPE=minio java -jar marvel-gateway-boot/target/marvel-gateway-boot-*.jar
```

关键配置：`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`、
`MINIO_PUBLIC_URL`（走 CDN/反代时填）、`MINIO_PUBLIC_READ`（默认 true，返回稳定直链）。

## 前端代码规范（oxc）

前端 lint 与格式化统一由 oxc 提供（`oxlint` + `oxfmt`），配置见 `.oxlintrc.json` / `.oxfmtrc.json`：

```bash
pnpm run lint          # oxlint（0 error 视为通过）
pnpm run lint:fix
pnpm run format        # oxfmt --write
pnpm run format:check  # CI 校验格式
```

CI 在 `pnpm install` 后、`build` 前执行 `lint` 与 `format:check`。

## 模块结构（= 未来微服务边界）

```
marvel-common        纯工具：统一返回体、异常、常量
marvel-api           模块间契约：system-api（未来变 Feign 模块）
marvel-framework     技术装配：Sa-Token、MyBatis-Plus、Redis、全局异常
marvel-modules
  module-system      用户/角色/菜单/部门/字典/参数/公告/日志/在线用户（未来 system 服务）
  module-auth        登录/验证码/动态路由（未来 auth 服务）
  module-infra       文件存储/定时任务/服务监控（未来 infra 服务）
marvel-gateway-boot  启动器 + Flyway 迁移脚本
marvel-ui            Vue3 前端
```

**硬性规则**：模块间只能依赖 `marvel-api` 中的接口（如 `SystemApi`），跨模块禁止直接查表；API 路径按域分段（`/system/**`、`/infra/**`、`/auth/**`），与未来网关路由一致。

## 本地运行

1. 启动 MySQL（root/root，或用 `MYSQL_PASSWORD` 环境变量覆盖）与 Redis，创建数据库：
   ```sql
   CREATE DATABASE marvel DEFAULT CHARACTER SET utf8mb4;
   ```
2. 后端（Flyway 自动建表并写入初始数据）：
   ```bash
   mvn -DskipTests install
   cd marvel-gateway-boot && mvn spring-boot:run
   ```
3. 前端（包管理器统一为 pnpm，仓库只保留 pnpm-lock.yaml）：
   ```bash
   cd marvel-ui && pnpm install && pnpm run dev
   ```
4. 访问 http://localhost:5173 ，默认账号 **admin / admin123**

## 演进 Spring Cloud 的改造点

| 现状（单体） | 拆分后（微服务） |
|---|---|
| module-system/auth/infra 同进程 | 各模块独立部署为服务 |
| `SystemApi` 进程内实现（SystemApiImpl） | 替换为 Feign 客户端 |
| marvel-gateway-boot 聚合所有 Controller | Spring Cloud Gateway 按路径前缀路由 |
| Sa-Token + Redis 共享会话 | 各服务共享同一 Redis 会话即可 |
| marvel-framework 本地依赖 | 抽象为私有 starter |

## 功能清单

- **首页看板**：用户/在线/今日登录/操作概览 + 登录趋势、操作趋势、模块分布、任务执行统计（ECharts）
- **个人中心**：基本资料（含头像上传，随存储实现落地 local/MinIO）、修改密码（入口在顶栏用户下拉）
- **RBAC 权限**：用户/角色/菜单/部门管理，动态路由，数据权限（`@DataScope` 按部门过滤）
- **认证**：登录/登出、验证码（可在参数配置中开关）、登录失败保护、在线用户列表与强制下线
- **系统工具**：字典管理、参数配置、通知公告（站内消息中心：已读/未读、全部已读、单条/批量删除）、定时任务（页面化管理 + 执行日志）
- **审计**：操作日志（注解 `@Log` 自动记录）、登录日志，支持查询/删除/清空/导出
- **运维**：服务监控（JVM/系统指标）、缓存监控与管理、文件管理（本地存储）、用户/日志 Excel 导入导出

## 路线图

- 已完成：上述功能清单全部条目（原一/二/三期规划）
- 规划中：
  - 对象存储扩展：已内置 MinIO（本地实现保留）；如需 OSS/COS 继续实现 `StorageService` 即可
  - 测试补强：auth 登录链路、权限鉴定核心（`StpInterfaceImpl`）等单测覆盖持续补齐；前端组件测试
- 安全基线：OpenAPI/Swagger 有意不启用；默认口令仅限本地演示（生产请修改并配置 `MYSQL_PASSWORD`）
