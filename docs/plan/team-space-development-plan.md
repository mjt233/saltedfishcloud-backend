# 团队空间（Team Space）开发计划

> 本文档为咸鱼云网盘「团队空间」功能的前后端联合开发计划，存放于后端仓库 `docs/plan/`（该目录未纳入 mkdocs 发布导航，仅作内部计划文档）。
>
> 关联仓库：后端 `saltedfishcloud-backend`（本文档所在仓库）、前端 `saltedfishcloud-frontend`。

## 1. 背景与目标

为网盘增加团队协作空间能力：

- 用户可以创建团队、管理团队成员，并为成员设置角色权限：**读 / 写 / 团队管理**。
- 添加成员时支持按**用户名或邮箱**快速搜索已注册用户。
- 支持为尚不存在的用户创建「待注册」成员：生成**邀请链接**，受邀人在受邀加入页面设置用户名与密码后自动创建咸鱼云账号并加入团队，简化新用户注册流程。
- 待注册成员在成员列表中可见并标记「未注册」，可随时**复制邀请链接**（支持撤销、重发）。
- 明确范围外：**系统存储空间配额**不在本需求内。

## 2. 需求确认结论（已与需求方逐项确认）

| # | 决策项 | 结论 |
|---|--------|------|
| 1 | 模块形态 | 独立插件 `sfc-ext-team` + 核心最小化鉴权扩展点（UID 访问权提供者 SPI） |
| 2 | 团队文件组织 | 团队专用 UID 空间：每个团队分配内部 UID，复用现有文件体系 |
| 3 | 角色语义 | 递进包含：团队管理 ⊇ 写 ⊇ 读；「写」= 上传/新建/改名/移动/删除/覆盖 |
| 4 | 团队创建权限 | 所有注册用户可创建（预留可配置的数量上限，默认不限） |
| 5 | 所有者模型 | 创建者=所有者（OWNER），不可被移除/降级，可转移所有权；仅所有者可解散团队 |
| 6 | 已有用户加入 | 直接加入（无需对方同意），对方下次登录即可看到团队空间 |
| 7 | 待注册成员账号 | 受邀人在接受页自定用户名（创建邀请时用户名/邮箱可留空）；邀请链接代替邮箱验证码 |
| 8 | 邀请链接规则 | 单次使用 + 7 天有效（可配置）；可随时撤销/重发（旧链接即时作废） |
| 9 | 系统管理员 | 拥有所有团队的完整管理权限（与现有「管理员可访问一切」模型一致） |
| 10 | 解散团队文件去向 | 立即删除（二次确认；系统无回收站，经 `DiskFileSystem.deleteFile` 直接删除） |
| 11 | 通知与审计 | v1 仅提供复制链接；成员/权限/邀请等关键操作落审计日志表（v1 不做展示界面） |
| 12 | 实现路线 | 保留核心 SPI 微改造路线（sfc-api/sfc-core 纯新增 + 单点接入，无插件时行为不变） |
| 13 | 前端主模块 | 除「导出 FileExplorer 组件」一行外零改动；团队文件浏览由插件实现 `TeamFileSystemHandler`（实现 `FileSystemHandler` 接口）+ 宿主 `FileExplorer` 组件渲染（参考 `FileShareDirBrowser`） |

## 3. 现状分析

### 3.1 后端架构

- 多模块 Maven 项目（Java 25），构建顺序：`sfc-api` → `sfc-archive`/`sfc-task`/`sfc-rpc` → `sfc-core` → `sfc-ext`。
- `sfc-api`：共享模型、`BaseModel`→`AuditModel` 实体基类、`BaseRepo`（JpaRepository + JpaSpecificationExecutor）、异常与校验工具。
- `sfc-core`：Spring Boot 主应用，聚合 security/config/controller/service；插件类加载与管理（`ext` 包）。
- `sfc-ext`：独立插件模块目录，先例 `sfc-ext-quick-share`（自带 Controller/Repo/Service/Model + AutoConfiguration + `plugin-info.json`，`loadType=merge`）。

### 3.2 用户体系

- `user` 表（`UserInfo`）：`user` 唯一（登录名）、`pwd` 加盐哈希、`type`（普通/管理员）、`email` 可选绑定、`quota` 暂未启用。
- `UserService` 已有能力：`searchUsers(keyword, page)`（关键词搜索）、`getUserByEmail`、`findBaseInfoByIds`、`addUser(...)`（含无需邮箱验证码的建号路径，用于管理员建号）。
- 公开注册接口要求邮箱验证码，注册开关判断位于 Controller 层。

### 3.3 文件权限模型

- 文件按 `uid + path` 定位：公共资源 `uid=0`，私人资源 `uid>0`。
- 权限判定集中在 `UIDValidator.validate(user, uid, publicOnlyAdmin)`：本人或管理员可操作私人 uid；公共资源读对所有人开放、写仅管理员。
- 文件读写接口 `ResourceController`（`/api/resource/{uid}...`），uid 参数带 `@UID` 注解；写操作（上传/建目录/删除/移动/复制/改名/解压/覆盖等）目前依赖 UIDValidator 的布尔校验。
- 结论：团队空间复用现有文件栈的最佳路径是「团队专用 UID + 核心授权扩展点」，可自动继承断点续传、秒传（md5）、缩略图、分享、WebDAV 等全部既有能力（系统当前无回收站）。

### 3.4 插件机制

- 后端插件：`sfc-ext` 下 Maven 模块（parent=`com.xiaotao:sfc-ext`）；`plugin-info.json`（`loadType=merge`）；Spring Boot 集成经 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` + `@ComponentScan`/`@EntityScan`/`@EnableJpaRepositories`；特性开关经 `HelloService.setFeature`。
- 前端插件：`sfc-ext/sfc-ext-{id}`，`main.ts` 入口经 `window.bootContext.addProcessor` 注册组件与菜单（boxMenu 模式，参考 `sfc-ext-quick-share`）；对话框统一 `SfcUtils.openComponentDialog`；对主模块 `sfc-common` 的非 type 导入仅允许 `sfc-common`、`sfc-common/components`、`SfcUtils`（`externalSfcCommonPath` 白名单，不含 router）。

### 3.5 前端架构

- Vue 3 + Vuetify 4 + Tailwind/SCSS；顶层仅 `/`（用户界面）与 `/admin`（管理员界面）两个路由，其余页面必须为其子路由；`/` 下存在 `/**` catch-all（`allowNoLogin: true`）；hash 路由模式；开发端口 4000。
- 插件菜单经 `context.menu.boxMenu`（含 `MenuHelper.addMoreBoxMenu`）扩展，点击菜单项切换 `ctx.currentComponent` 渲染组件。
- 前端 `AGENTS.md` 约束：全面 jsdoc、async/await、对话框用 `SfcUtils.openComponentDialog`、修改后 `npm run typecheck` 验证、组件与样式规范文档。

## 4. 总体架构

### 4.1 模块划分

- 后端：新建插件模块 `sfc-ext/sfc-ext-team`（artifactId `sfc-ext-team`，包名 `com.sfc.team`）；核心小改造仅两处：`sfc-api` 新增访问级别枚举与 SPI 接口，`sfc-core` 新增聚合解析器并接入 UIDValidator 与写操作校验。
- 前端：新建 `saltedfishcloud-frontend/sfc-ext/sfc-ext-team`。
- 团队业务（团队/成员/邀请/审计）全部在插件内实现；核心只认识「访问级别」抽象，不感知团队概念——未来其他插件（如组织架构）可复用同一 SPI。

### 4.2 团队 UID 方案

- **主方案（snowflake id 直取）**：`sys_team` 实体继承 sfc-api `BaseModel`，主键经 `@SnowFlakeIdGenerator` 由 `IdUtil.getId()` 自动生成全局唯一 id，**直接令 `teamUid = team.id`**，不再使用段偏移。
    - 系统内所有实体 id（含用户 id，user 表同样继承 BaseModel）均出自同一 SnowFlake 实例（DCID/MID 区分节点），团队 uid 与用户 uid 永不相撞；`UIDValidator` 中 `uid == user.getId()` 的判断天然不误伤。
    - Long 序列化：`JacksonConfig` 已将 Long/long 序列化为字符串，前端 `IdType = number | string`，既有用户 uid（snowflake 大数）已走通全链路，团队 uid 无 JS 精度问题。
    - 团队 uid 与用户 id 同为 snowflake 大数、无数值区间可判断，核心 resolver 依赖 provider 查库（查询与缓存策略见 §5.3）。
- **备选方案一（段偏移）**：若未来希望 resolver 以 O(1) 数值区间判断团队 uid，可退回「团队自增序号 + `TEAM_UID_BASE` 段偏移」；当前无必要，不采用。
- **备选方案二（虚拟用户）**：在 user 表插入 `type=TEAM` 的不可登录虚拟行作为团队 uid。当开发中发现大量「uid 必须对应 user 表记录」的强假设导致功能缺陷时切换；切换成本：核心登录拦截 + 用户搜索/列表排除。
- 展示层：凡展示「资源所有者」处，前端对 teamUid 映射显示团队名（后端提供团队信息批量查询接口支撑）。

### 4.3 路线对比：零核心改动（纯插件）vs 核心 SPI 微改造

经源码核实（`DiskFileSystem` 服务层不含 uid 鉴权、插件可注入 core Bean——quick-share 先例、`JacksonConfig` 已把 Long 序列化为字符串），**零核心改动路线技术上可行**：插件自带团队文件接口层，直接以 teamUid 调用 `DiskFileSystem` 服务层 API，角色校验完全在插件内完成；core 现有 `UIDValidator` 对 teamUid 天然拒绝一切非本人访问，反而构成兜底防线。

| 维度 | 核心 SPI 微改造（默认路线） | 零核心改动（纯插件） |
|------|--------------------------|---------------------|
| sfc-api 改动 | +UidAccessLevel、+UidAccessProvider（纯新增约 40 行，不触碰现有类） | 无 |
| sfc-core 改动 | +UidAccessResolver；UIDValidator 单点接入；写接口校验点追加（约 100-200 行，集中 2-3 个文件；无插件时行为完全不变） | 无 |
| 插件后端增量 | 团队/成员/邀请业务 + Provider 实现（约 1500 行） | 左列全部 + 对等复制 FileController 的文件接口层（列表/建目录/断点上传/下载/预览/删除/移动/复制/改名/搜索，10+ 端点，约 400-600 行额外） |
| 能力完整度 | 团队空间自动继承全部既有文件能力（WebDAV/挂载/分享/断点/缩略图/秒传） | 断点上传可用（@BreakPoint 为注解切面，对插件 Controller 同样生效）；缩略图可用（按 md5 取图、无 uid 闸门）；秒传可用（FS.quickSave）；WebDAV/挂载/分享不可用（本评估已排除） |
| 前端 | 插件实现 `TeamFileSystemHandler`（实现 `FileSystemHandler` 接口，经 window.API 调核心 `/diskFile/{teamUid}` 系列端点）+ 宿主 `FileExplorer` 组件渲染（参考 `FileShareDirBrowser` 用法）；sfc-common 主模块仅新增一行 `FileExplorer` 组件导出 | 同左；但端点指向插件自带文件接口层 |
| 安全面 | 单一闸门：resolver 统一放行，角色在 provider 一处表达 | 两套鉴权逻辑并存（core UIDValidator + 插件角色校验），插件接口层遗漏校验即越权 |
| 后续维护 | core 新增文件能力自动适用团队空间 | core 文件能力演进需插件跟进复制 |

结论与建议：

- 若「不改 sfc-api/sfc-core」是硬约束：纯插件路线可行，复杂度主要转移到插件文件接口层与前端文件 UI 的重复实现，且部分既有能力（WebDAV 等）无法覆盖团队空间。
- 若无硬约束：推荐默认路线（核心 SPI 微改造）——核心改动为纯新增类与单点接入，无插件时行为不变，总体新增代码量与长期维护成本最低。
- 渐进式方案（可选）：阶段一按纯插件路线实现（插件内文件接口层做成独立适配层，内部统一走 `DiskFileSystem`）；若后续能力缺口不可接受，阶段二再申请核心 SPI（业务与数据模型不变，仅替换文件访问通道）。此方案下 M1 里程碑替换为「插件文件接口层」。
- **已定稿**：采用核心 SPI 微改造路线；团队文件浏览采用「插件实现 `TeamFileSystemHandler` + 宿主 `FileExplorer` 渲染」（sfc-common 主模块仅新增一行组件导出）。

**范围收敛后的成本重估（仅团队内文件增删改查，不考虑 WebDAV/挂载/分享）**

- 后端：纯插件增量降为「对 `DiskFileSystem` 服务层的薄封装接口层」——list/mkdir/quickSave/upload/download/delete/move/copy/rename 共 8-10 个端点、约 300-500 行，角色校验集中在插件内单一访问检查器；`ProxyProcessor` 是 `@Around("@annotation(BreakPoint)")` 注解切面，插件 Controller 标注 `@BreakPoint` 即可复用断点上传（v1 可选）；缩略图走 core 按 md5 取图端点（无 uid 闸门）免费获得；删除即直接删除（系统无回收站，`FileController.delete` 直调 `DiskFileSystem.deleteFile`）。
- 前端（已定稿）：宿主 `FileExplorer` 组件接受 `fileSystemHandler` prop（已核实其全部文件操作均经注入的 handler 执行），且 `FileSystemHandler` 类型经 `sfc-common` 根导出；插件实现 `TeamFileSystemHandler`（7 个方法，经 window.API 调核心 `/diskFile/{teamUid}` 系列，SPI 已放行成员），并以 `FileShareDirBrowser` 为参考将 `FileExplorer` 作为团队文件浏览组件渲染（readOnly 按角色控制写入口、toolButtons 复用宿主默认按钮）；因 `FileExplorer` 此前未从 `sfc-common/components` 导出，主模块新增一行导出（向后兼容），其余主模块零改动。
- 结论：已定稿方案 = **核心 SPI 微改造 + 前端主模块一行导出**；团队文件浏览复用宿主 `FileExplorer` 完整能力（列表/上传/新建/右键菜单/预览/缩略图/视图切换），插件仅需实现 `FileSystemHandler` 接口（约 150 行）与包装组件；SPI 路线提供行为一致性（团队空间=标准 uid 空间、未来文件能力自动继承）与单一鉴权闸门。

### 4.4 架构图（文字版）

    ┌────────────────────────────────────────────────────────────┐
    │ 前端 sfc-ext/sfc-ext-team（菜单/组件/受邀注册页）              │
    └──────────────┬──────────────────────────┬──────────────────┘
                   │ /api/team/*（团队/成员/邀请）│ /api/resource/{teamUid}/*（复用现有文件接口）
    ┌──────────────▼──────────────────────────▼──────────────────┐
    │ 后端插件 sfc-ext-team（Controller/Service/Repo/Entity）        │
    │   - 团队/成员/邀请/审计业务                                     │
    │   - 实现 UidAccessProvider（查询 sys_team_member/invite）      │
    └──────────────┬─────────────────────────────────────────────┘
                   │ 注册为 Spring Bean
    ┌──────────────▼─────────────────────────────────────────────┐
    │ 核心：UidAccessResolver ──► UIDValidator / 写操作级别校验       │
    │       └──► 现有文件栈（ResourceController/缩略图/WebDAV/分享）│
    └────────────────────────────────────────────────────────────┘

### 4.5 存储方案评估：团队文件不使用 AttachStorageManager

- **结论**：团队文件主体**不使用** `AttachStorageManager`/`AttachStorage`。该机制定位为「不适合通过主文件系统管理的辅助数据」的轻量存储（缩略图/头像/断点分片/快传临时文件等，见 `docs/develop/framework/filesystem/attach-storage.md` 已注册域一览），并非业务文件存储。
- 不适用理由：
    1. **无权限模型**：存储域内无任何 ACL，能拿到 `AttachStorage` 实例即可全量读写，团队「读/写/管理」三角色校验全部压在插件接口层且无兜底。
    2. **无元数据与目录语义**：仅提供路径级 blob 操作（getFile/saveFile/delete/listFiles），目录树、文件记录（大小/时间/md5）、搜索、秒传、并发写协调需从零实现。
    3. **仅本地磁盘**：`DefaultAttachStorage` 固定落在 `sys.store.root/attach/<domainId>`，不参与主存储后端（minio/oss/hadoop 等）选择，无法随主存储迁移/备份，data-manager 等管理工具不识别这些数据。
    4. **与主文件系统能力脱钩**：缩略图管线、md5 去重、断点缓存均基于主文件系统的文件记录，附属存储无法复用。
    5. **域语义不匹配**：存储域是进程内注册表（`putIfAbsent` 的缓存域语义），按团队动态注册（如 `team_<id>`）治理别扭，团队解散后的数据清理也需插件自行兜底。
- **正确方案**：团队文件存放于主文件系统——以 teamUid 调用 `DiskFileSystem` 服务层 API（插件直调或经核心 SPI 放行后的标准接口），免费获得 md5 秒传、文件记录、搜索、缩略图、断点上传、存储后端选择与迁移能力。
- AttachStorage 在团队功能中的合适用途：**团队附属缓存**（如团队头像 logo，注册 `team_avatar` 域，与 `user_avatar` 域同类）。

## 5. 核心改造设计（sfc-api / sfc-core）

### 5.1 新增 UidAccessLevel（sfc-api）

新包 `com.xiaotao.saltedfishcloud.service.permission`，枚举：

    NONE / READ / WRITE / MANAGE

- `READ`：可读取资源；`WRITE`：可修改资源；`MANAGE`：完全控制（含成员管理语义由插件解释）。
- 全部字段与语义补全 JavaDoc。

### 5.2 新增 UidAccessProvider SPI（sfc-api）

    public interface UidAccessProvider {
        /**
         * 返回指定用户对指定 uid 资源的访问级别；与本提供者无关时返回 NONE。
         */
        UidAccessLevel getAccessLevel(UserPrincipal user, long uid);
    }

契约：无关联必须返回 `NONE`；实现须幂等、只读、不得抛出异常（异常按 `NONE` 处理）。

### 5.3 新增 UidAccessResolver（sfc-core）

- 聚合所有 `UidAccessProvider` Bean（`List<UidAccessProvider>` 注入，允许为空）。
- `resolve(user, uid)` 优先级：
    1. `user == null` → `NONE`
    2. 系统管理员 → `MANAGE`
    3. `uid == 0`（公共资源）→ `READ`
    4. `uid == user.getId()` → `MANAGE`
    5. 遍历 providers 取最高级别（无 provider 或全 NONE → `NONE`）
- 提供便捷方法 `checkRead` / `checkWrite` / `checkManage`；`checkWrite` 内部保留「公共资源写仅管理员」的既有规则。
- provider 查询与缓存：团队 uid 与用户 id 同为 snowflake 大数，无法数值区间判断，provider 需查库。推荐策略：provider 一次查询「当前用户的全部团队成员记录」，判断目标 uid 是否命中（单次查询）；配合本地缓存（短 TTL + 插件内成员/角色变更时主动失效），避免每次团队文件请求多一次 DB 查询。个人空间请求（uid=本人）在 resolver 第 4 步短路，不受影响。

### 5.4 UIDValidator 接入（sfc-api/UIDValidator.java）

- 在 `user.getId() != uid 且非管理员` 分支前查询 resolver：若返回 `≥ READ` 则视为通过，维持现有布尔语义（读类校验放行任意访问级别）。
- 获取 resolver 的方式（经 Spring 上下文持有器或静态注册单例）在开发时确定，原则是不引入循环依赖、插件缺席时优雅降级。
- **兼容性**：未安装任何 provider 时行为与现状完全一致。

### 5.5 写操作级别校验（sfc-core）

- 新增校验能力（resolver 静态方法或独立 `UidAccessValidator`）：`checkWrite(uid)` 要求 `resolve ≥ WRITE`；公共资源 uid 写仍仅管理员（沿用 `publicOnlyAdmin` 规则）。
- 替换点清单（开发时逐处核对，用代码检索 UIDValidator 调用点全覆盖）：
    - `ResourceController` 写方法：upload（含覆盖）、mkdir、delete、move、copy、rename、extract 等。
    - 压缩/解压目标目录、批量操作、元数据写入口（`FileRecordService` 相关）。
- 原则：读类校验维持布尔语义（READ 即通过）；写类校验要求 WRITE；批量删除等破坏性操作可按角色映射要求 MANAGE（见 6.4 角色映射，v1 默认与「写」同级或收紧为 MANAGE，开发时定稿）。

### 5.6 兼容性与回归基线

- 未装插件：个人 uid、公共资源、管理员行为均不变。
- 安装插件：仅新增「团队 uid + 成员」放行路径，其余不变。
- 回归重点：个人网盘全操作、公共资源读写、quick-share、WebDAV、断点续传、缩略图。

## 6. 数据模型设计（插件 JPA 实体）

实体写法对齐 `sfc-ext-quick-share` 的实体模板（继承 sfc-api `BaseModel` 等），开发时以 `QuickShare` 实体为参考；Repo 按 `generate-jpa-repository` 技能规范生成（继承 `BaseRepo`，分页用 `Pageable`/返回 `Page`）。

### 6.1 sys_team（团队）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK（BaseModel `@SnowFlakeIdGenerator` → `IdUtil.getId()` 生成） | 团队 id，同时作为团队 uid（`teamUid = id`） |
| name | VARCHAR(64) | 团队名 |
| description | VARCHAR(255) 可空 | 团队描述 |
| owner_uid | BIGINT | 所有者用户 id（user 表） |
| status | TINYINT | 1=正常 2=已解散（保留行用于审计） |
| created_at / updated_at | DATETIME | 继承基类字段 |

### 6.2 sys_team_member（团队成员，已注册用户）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK 自增 | |
| team_id | BIGINT | 团队 id |
| uid | BIGINT | 成员用户 id |
| role | TINYINT | 1=READ 2=WRITE 3=MANAGE 4=OWNER |
| status | TINYINT | 1=正常 2=已移除 |
| created_at | DATETIME | 入团时间 |

唯一索引 `(team_id, uid)`。

### 6.3 sys_team_invite（待注册成员邀请）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK 自增 | |
| team_id | BIGINT | 团队 id |
| inviter_uid | BIGINT | 邀请人用户 id |
| username | VARCHAR 可空 | 预填用户名（登录名，接受页可改/可填） |
| email | VARCHAR 可空 | 预填邮箱（接受后绑定） |
| role | TINYINT | 接受后生效的角色（1/2/3，不含 OWNER） |
| token | VARCHAR(64) 唯一 | 随机邀请 token |
| status | TINYINT | 1=待接受 2=已接受 3=已撤销 |
| expire_at | DATETIME | 过期时间（默认创建时间 + 7 天，可配置） |
| accepted_uid | BIGINT 可空 | 接受后回填的新用户 id |
| created_at | DATETIME | 创建时间 |

「成员列表」聚合视图 = 正常成员（sys_team_member）+ 有效待接受邀请（sys_team_invite，前端标记「未注册」）。

### 6.4 角色映射

| 团队角色 | UidAccessLevel | 文件能力 |
|----------|----------------|----------|
| READ | READ | 查看/下载/预览 |
| WRITE | WRITE | + 上传/新建/改名/移动/删除/覆盖 |
| MANAGE | MANAGE | + 成员与权限管理、团队设置 |
| OWNER | MANAGE | + 解散团队、转移所有权（业务接口单独校验 OWNER） |

### 6.5 sys_team_audit_log（审计日志，v1 仅落库）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK 自增 | |
| team_id | BIGINT | 团队 id |
| operator_uid | BIGINT | 操作人 |
| action | VARCHAR(32) | CREATE_TEAM / ADD_MEMBER / REMOVE_MEMBER / UPDATE_ROLE / CREATE_INVITE / REVOKE_INVITE / REGENERATE_INVITE / ACCEPT_INVITE / TRANSFER_OWNER / DISSOLVE_TEAM / QUIT_TEAM |
| target | VARCHAR(64) | 操作对象（成员/邀请标识） |
| detail | VARCHAR(512) | 明细（角色变更、预填信息等） |
| created_at | DATETIME | 时间 |

## 7. 后端 API 设计（插件 Controller，前缀 `/api/team`）

通用规范（遵循后端 `AGENTS.md`）：

- 请求方法仅 `GET` / `POST`；不使用路径参数占位符（token 等经 `@RequestParam` 传递）。
- 分页统一 `CommonPageInfo` + `PageableRequest`；返回 `JsonResult`；业务异常抛 `JsonException`。
- 除邀请查询/接受接口外默认要求登录；管理接口在服务层校验角色；uid 参数标注 `@UID`。
- 全部方法与参数补全 JavaDoc。

### 7.1 团队管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/team/create | 创建团队（name、description），创建者成为 OWNER | 登录用户（受「创建上限」配置约束） |
| POST | /api/team/delete | 解散团队：删团队文件（走现有删除链路）→ 团队/成员/邀请置状态 | 仅 OWNER 或系统管理员 |
| POST | /api/team/updateInfo | 修改名称/描述 | MANAGE 及以上 |
| GET | /api/team/list | 我加入的团队列表；`all=true` 时管理员查全部 | 登录用户 |
| GET | /api/team/detail | 团队信息 + 我的角色 + 成员聚合列表（含待注册成员） | 团队成员（待注册成员见 memberList） |
| POST | /api/team/quit | 退出团队 | 非 OWNER 成员（OWNER 须先转移） |
| POST | /api/team/transferOwner | 转移所有权（目标须为正常成员）→ 原 OWNER 变 MANAGE | 仅 OWNER |

### 7.2 成员与邀请管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/team/memberList | 成员分页列表：正常成员 + 待注册成员（标记未注册、邀请状态/过期时间、token） | MANAGE 及以上 |
| POST | /api/team/addMember | 按 uid 直接加入已注册用户（校验存在且未在团队） | MANAGE 及以上 |
| POST | /api/team/removeMember | 移除成员（不可移除 OWNER） | MANAGE 及以上 |
| POST | /api/team/updateMemberRole | 修改角色（不可修改 OWNER 角色） | MANAGE 及以上 |
| POST | /api/team/createInvite | 为不存在用户创建邀请（teamId、username 可空、email 可空、role）→ 返回 token 与完整链接 | MANAGE 及以上 |
| GET | /api/team/getInviteLink | 获取某待注册成员当前有效邀请链接（复制用） | MANAGE 及以上 |
| POST | /api/team/revokeInvite | 撤销邀请（token 即时作废） | MANAGE 及以上 |
| POST | /api/team/regenerateInvite | 重发邀请：旧 token 作废，生成新 token 并重新计时 | MANAGE 及以上 |

### 7.3 用户搜索

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/team/searchUser | 按用户名/邮箱关键词搜索已注册用户，返回 uid/username/email 基础信息（复用 `UserService.searchUsers`/`getUserByEmail`） | 登录用户（限 MANAGE 成员在添加成员场景调用，服务层校验） |

### 7.4 邀请公开接口（@AllowAnonymous）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/team/inviteInfo | 按 token 查询邀请信息（团队名、邀请人、预填用户名/邮箱、过期状态），供受邀页展示 |
| POST | /api/team/acceptInvite | 接受邀请：校验 token（PENDING + 未过期）→ 校验用户名唯一与密码强度（复用 `@Username`/`@Valid`）→ `UserService.addUser`（无邮箱验证码路径，绕过公开注册开关）→ 创建正常成员（角色=邀请指定）→ 邀请置已接受并回填 uid |

### 7.5 团队文件访问

- 前端在团队空间上下文以 `teamUid` 调用既有 `/api/resource/{teamUid}/...` 系列接口（列表/上传/下载/预览/删除/移动/复制/改名等），鉴权由核心 resolver + 写级别校验完成。
- 插件可选提供 `GET /api/team/fileOwnerInfo`（批量查询 teamUid → 团队名），供展示层映射。

## 8. 鉴权与安全设计

- **邀请 token**：`SecureRandom` 生成 32 字节随机串（base64url）；单次使用、默认 7 天有效（插件配置项可调）；撤销/重发即时作废旧 token；接受成功后失效。
- **接受接口防护**：v1 不做邮箱验证码（邀请 token 即验证凭据）；预留图形验证码扩展点；可选轻量防刷（标注为可选增强）。
- **绕过注册开关的合理性**：`acceptInvite` 走 `UserService` 内部建号路径而非公开注册 Controller，不受「禁止注册」开关影响——该行为是有意设计（管理员发出邀请=管理员授权代注册），在代码注释与文档中明示。
- **角色校验**：团队管理接口在服务层统一校验操作者角色 ≥ MANAGE；OWNER 专属操作（转移/解散）单独校验。
- **文件层**：核心 resolver 放行读、写级别校验放行写（见 §5）；前端按角色隐藏写入口，后端强制校验兜底。
- **审计**：§7 全部关键操作写 `sys_team_audit_log`。
- **密码**：复用现有加盐哈希与强度校验逻辑，不另造轮子。

## 9. 前端设计（saltedfishcloud-frontend/sfc-ext/sfc-ext-team）

### 9.1 插件结构与入口

    sfc-ext/sfc-ext-team/
    ├── main.ts                    # 入口：addProcessor 注册组件/菜单；监听路由处理受邀页
    ├── api.ts                     # /api/team 接口封装
    ├── model.ts                   # Team/TeamMember/TeamInvite/Role 等类型（jsdoc）
    └── components/
        ├── team-list-view.vue        # 我的团队列表 + 创建团队
        ├── team-space-view.vue       # 团队空间主视图（团队选择 + 团队文件浏览）
        ├── fileSystemHandler.ts      # TeamFileSystemHandler：实现宿主 FileSystemHandler 接口（经 window.API 调核心文件端点）
    ├── components/team-file-browser.vue  # 宿主 FileExplorer 包装组件（注入 handler + readOnly/toolButtons 控制）
        ├── team-member-manage.vue    # 成员管理（含待注册成员）
        ├── team-invite-accept.vue    # 受邀注册页（公开）
        └── dialogs/                  # 创建团队/添加成员/邀请确认/转移所有权/解散确认等对话框

- `main.ts` 经 `window.bootContext.addProcessor` 注册全局组件，经 `MenuHelper.addMoreBoxMenu` 或注入 boxMenu 新分组「团队空间」，菜单项 action 设置 `ctx.currentComponent`（对齐 `sfc-ext-quick-share` 模式）。
- 特性开关：`SfcUtils.getSystemFeature('team', 'isEnabled')` 为 false 时不注册任何 UI。
- 非 type 导入仅从 `externalSfcCommonPath` 白名单路径导入；宿主文件 API 经运行时全局 `window.API` 访问（不经 import，宿主插件开发文档明示可用），基础组件从 `sfc-common/components` 导入；若需要宿主未导出的能力（如 router），按 AGENTS.md 要求先提示用户扩展白名单。

### 9.2 页面与交互

- **我的团队列表**：卡片/表格展示团队（名称、我的角色、成员数）；「创建团队」按钮经 `SfcUtils.openComponentDialog` 打开创建对话框。
- **团队空间主视图**：左侧团队列表切换团队；右侧为宿主 `FileExplorer` 驱动的团队文件浏览组件（见 §9.4）。
- **成员管理**：成员表格（头像/用户名/邮箱/角色/状态）；待注册成员显示「未注册」徽标、邀请过期状态，操作列提供复制链接/重发/撤销；角色下拉修改（READ/WRITE/MANAGE）；移除成员。
- **添加成员对话框**：搜索输入框（用户名/邮箱）联想下拉；命中已注册用户 → 选角色直接添加；无命中 → 切换「创建新成员邀请」表单（用户名/邮箱可留空 + 角色）→ 创建成功展示链接与复制按钮。
- **所有者操作**：转移所有权（从 MANAGE 成员中选择）、解散团队（二次确认并提示文件将删除）。
- **受邀注册页（公开）**：读取 token → 展示团队名/邀请人 → 填写用户名（若未预填）与密码 → 提交 `acceptInvite` → 成功引导登录。

### 9.3 路由与菜单集成

- 遵守前端 AGENTS.md：不新增顶层路由；团队视图全部经 boxMenu 组件切换呈现。
- 受邀页路径 `/#/team-invite?token=xxx`（hash 路由命中 `/` 的 `/**` catch-all，`allowNoLogin` 已放行）；插件监听 `getContext().routeInfo` / `ROUTE_CHANGE` 事件，路径匹配 `/team-invite` 时设置 `currentComponent = team-invite-accept`。
- 备选方案：若开发时确认宿主放行 router 导入（扩展 `externalSfcCommonPath`），改用 `router.addRoute` 在 `/` 下注册显式子路由（更标准）；白名单扩展需先获得用户确认。

### 9.4 团队文件浏览（宿主 FileExplorer + TeamFileSystemHandler）

- 实现方式：参考 `FileShareDirBrowser` 的模式——插件实现 `TeamFileSystemHandler implements FileSystemHandler`（`fileSystemHandler.ts`，7 个方法：loadList/mkdir/deleteFile/uploadDirect/rename/getFileUrl/getCustomThumbnailUrl），经运行时全局 `window.API` 调用核心端点（`/diskFile/{teamUid}` 系列，SPI 已对团队成员放行）；组件 `team-file-browser.vue` 将 handler 作为 prop 注入宿主 `FileExplorer`（`FileExplorerProps.fileSystemHandler`，已核实其全部文件操作均经注入 handler 执行）。
- 下载/预览 URL：非挂载文件经 `SfcUtils.getApiUrl` + `window.API.resource.downloadFileByMD5`（按 md5 取文件，无 uid 闸门）；挂载文件回退 `getCommonResource(main, targetId=teamUid)`；缩略图返回 undefined 走 FileIcon 内置 md5 缩略图管线。
- 交互与能力：完整复用宿主 `FileExplorer`（列表/表格/网格视图切换、上传/新建目录工具栏、右键菜单、预览、README 预览、面包屑、自动高度等），与个人网盘页面体验一致。
- 角色控制：`readOnly = 我的角色 < WRITE`（READ 成员隐藏上传/新建等全部写入口与移动端浮钮，宿主菜单按只读态收敛）；工具栏按钮写角色复用宿主默认 `fileBrowserBtn`（上传/新建/挂载），只读角色传空数组；后端校验兜底。
- 主模块变更：`sfc-common/components/common/index.ts` 新增一行 `export { default as FileExplorer } from './FileExplorer/FileExplorer.vue'`（此前未导出，向后兼容）。
- 资源所有者展示处：teamUid 映射显示团队名（`fileOwnerInfo` 接口支撑）。

### 9.5 前端规范遵循（前端 AGENTS.md）

- 类/对象/字段/方法/interface/type/props/watch 全部 jsdoc；关键步骤行内注释；异步函数 async/await。
- 对话框统一 `SfcUtils.openComponentDialog`。
- 组件与样式参考 `docs/vue-component-style.md`、`docs/front-end-style.md`；路径选择优先复用 `PathSelector`。
- 修改代码后用 `npm run typecheck` 验证通过。

## 10. 与既有系统集成点

- **特性开关**：`HelloService.setFeature("team", TeamProperty)`（对齐 quickshare）；前端 `getSystemFeature` 判断。
- **插件配置**：`config-properties.json` 声明：团队创建数量上限（默认不限）、邀请有效期天数（默认 7）等。
- **开发环境**：`application-develop.yml` 增加 `plugin.extra-resource` 指向后端插件目录；前端插件按既有构建流程产出静态资源，随插件 jar `autoLoad` 分发。
- **用户搜索**：复用 `UserService.searchUsers` / `getUserByEmail` / `findBaseInfoByIds`。
- **文件系统**：团队 uid 空间自动继承断点续传/秒传（md5）/缩略图/分享/WebDAV 等既有能力。
- **编码规范**：JavaDoc 全覆盖；分页 `CommonPageInfo`；批量操作用 `CollectionUtils.partition` 拆分 + `DBUtils.batchInsert`；异常用 `JsonException`。
- **数据库**：JPA 自动建表（对齐现有插件机制，开发时确认）。

## 11. 非目标（范围外）

- 系统存储配额（明确不做）。
- 邮件通知、站内信通知（v1 仅复制链接）。
- 审计日志展示界面（v1 仅落库）。
- 更细粒度角色（仅上传等）、团队间共享、子团队层级。
- 邀请页图形验证码（预留扩展点）。
- 管理端（/admin）团队管理页面（系统管理员经 API 拥有完整权限，页面后续按需扩展）。

## 12. 开发里程碑与任务拆分

| 里程碑 | 内容 | 产出与验收 |
|--------|------|------------|
| M0 集成点确认 | 确认宿主扩展点：`window.API`（file/resource 模块）运行时可访问性与方法清单；`sfc-common/components` 导出的基础组件清单（FileIcon/FileMenu/GridMenu/LoadingMask/CommonPagination/PathSelector 等）；boxMenu 组件切换；后端 `UserService.addUser` 内部路径与 `@UID` 行为 | 《集成点确认清单》 |
| M1 核心 SPI 改造 | UidAccessLevel / UidAccessProvider / UidAccessResolver + UIDValidator 接入 + 写操作级别校验 | 未装插件行为不变（回归通过）；mock provider 可放行/拒绝 |
| M2 后端插件骨架 | Maven 模块、plugin-info.json、AutoConfiguration、实体/Repo（继承 BaseModel，主键经 `@SnowFlakeIdGenerator` 自动生成，无需手动分配 id）、特性开关、配置项 | 插件可加载、表自动创建、特性开关生效 |
| M3 团队与成员 API | 团队 CRUD/成员管理/角色/所有者操作 + 用户搜索 + 审计落库 | 接口自测通过；角色越权被拒 |
| M4 邀请体系 API | 创建/撤销/重发/查询/接受（注册+入团） | 全生命周期（待接受→接受→失效）自测通过 |
| M5 前端插件骨架 | 目录、main.ts、api/model、菜单入口、团队列表 + 创建对话框、团队文件浏览组件骨架（基于 window.API + 宿主导出组件） | typecheck 通过；列表/创建/文件浏览骨架可用 |
| M6 成员管理与邀请 UI | 添加成员对话框（搜索/新建邀请）、未注册标记、复制/撤销/重发链接、受邀注册页 | 全流程可用；未注册成员正确展示 |
| M7 团队文件浏览联调 | TeamFileSystemHandler + FileExplorer 与核心端点联调（window.API + teamUid）、读写权限前后端联调、READ 只读态 | 角色矩阵行为正确；文件增删改查全流程可用 |
| M8 收尾 | 边界场景（解散/转移/退出/过期邀请）、全量回归（个人/公共/分享/WebDAV）、文档 | 验收标准全部通过 |

## 13. 风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| 核心 UIDValidator 改造影响面大 | 既有文件功能回归 | 保持「无 provider 行为不变」设计；建立回归基线清单 |
| 写操作校验点遗漏（部分写路径不经 UIDValidator） | 越权写入 | §5.5 清单逐处核对；代码检索 UIDValidator 调用点全覆盖 |
| teamUid 与「uid=用户」假设冲突（所有者展示、按 uid 查用户等） | 显示异常/查询失败 | 团队 uid 与用户 id 同源 snowflake、永不碰撞；展示层映射团队名；必要时切换虚拟用户备选方案 |
| 前端插件无法导入 router（白名单限制） | 路由注册受限 | boxMenu + routeInfo 监听方案；必要时向用户申请扩展白名单 |
| FileExplorer 组件此前未从 sfc-common/components 导出 | 插件无法按白名单导入 | 主模块新增一行导出（已实施，向后兼容）；若后续需要更多内部组件再按 AGENTS.md 提示用户扩展导出 |
| 邀请注册与现有注册体系差异 | 注册开关被绕过引发争议 | 有意设计并明示；token 强度 + 有效期 + 撤销兜底 |
| 多人并发写团队同一文件 | 数据冲突 | 沿用现有文件锁/冲突机制（开发时确认），不在本需求内新增 |
| Java 25 编译环境要求 | 构建失败 | 本地构建遵守根 pom 环境要求 |

## 14. 验收标准

1. 团队全流程：创建/改名/解散/转移所有权/退出均可用，解散后团队文件被删除、审计留存。
2. 角色矩阵：READ 仅可读；WRITE 可上传/新建/改名/移动/删除/覆盖；MANAGE 可管理成员与团队设置；直接调用文件 API 越权（如 READ 调上传接口、非成员访问团队 uid）被后端拒绝。
3. 成员管理：按用户名/邮箱搜索并直接添加已注册用户；为不存在用户创建邀请，成员列表显示「未注册」标记，可复制/撤销/重发链接。
4. 受邀注册：链接打开 → 设置用户名与密码 → 自动创建咸鱼云账号并以指定角色入团 → 链接失效；过期/已撤销链接不可用；重发后旧链接作废。
5. 系统管理员可查看/管理所有团队与团队文件。
6. 关键操作审计日志落库。
7. 未安装/停用插件时系统既有行为完全不变（核心兼容回归）。
8. 前端 `npm run typecheck` 通过；前后端代码符合各自 AGENTS.md 规范（JavaDoc/jsdoc、CommonPageInfo、对话框规范等）。
9. 前端 sfc-common 主模块仅新增一行 `FileExplorer` 组件导出，其余代码零改动；团队文件浏览由插件 `TeamFileSystemHandler`（实现 `FileSystemHandler` 接口）+ 宿主 `FileExplorer` 组件实现。

## 15. 后续扩展可能

- 邮件/站内信通知、审计日志展示界面、邀请页图形验证码。
- 细粒度角色（仅上传等）、管理端团队页面。
- 若未来引入配额体系，团队 uid 空间可独立计量。

## 16. 实现状态记录（2026-08-18）

- **M1 核心 SPI 已完成**：sfc-api 新增 `service/permission` 包（UidAccessLevel/UidAccessProvider/UidAccessResolver/UidAccessValidator）；UIDValidator 接入 resolver（读校验任意级别通过、写校验要求 WRITE，@UID(true) 语义自动升级）；FileController.move 源空间增加 `validateTeamModifyIfProviderManaged`；无插件时行为不变。
- **M2-M4 后端插件已完成**：`sfc-ext/sfc-ext-team`（实体 sys_team/sys_team_member/sys_team_invite/sys_team_audit_log、Repo、TeamService/TeamInviteService、TeamController 全部 /api/team 接口、TeamAccessProvider 实现 UidAccessProvider、TeamProperty 特性开关、config-properties.json、AutoConfiguration）；已注册到 sfc-ext 模块与 application-develop.yml 的 extra-resource。
- **M5-M6 前端插件已完成**：`saltedfishcloud-frontend/sfc-ext/sfc-ext-team`（main.ts 经 boxMenu 注册「团队空间」入口、api.ts/model.ts/store.ts、team-space-view/team-member-manage/team-file-browser 组件，sfc-common 主模块零改动）；受邀注册页为后端插件静态页 `src/main/assert/static/invite.html`（免登录，URL：/api/plugin/sfc-ext-team/resource/invite.html?token=xxx）；构建产物 index.umd.js/style.css 已随 build-ext 自动复制到后端插件 static 目录；.env.development VITE_PLUGINS 已加入 team。
- **M7-M8 验证已完成**：后端编译通过（build_project 无问题）、前端 typecheck 通过；本地起服冒烟测试全链路通过（创建团队/搜索用户/直接加成员/创建邀请/匿名受邀页/接受注册/登录可见/WRITE 读写文件/READ 越权被拒/撤销与重发/旧 token 作废/解散团队后空间不可访问），冒烟数据已清理（dev 库保留 invitee1/invitee2 测试账号，可忽略或自行删除）。
- **迭代二（团队文件浏览改用宿主 FileExplorer）**：按用户要求将团队文件浏览从自建组件改为「宿主 `FileExplorer` + 插件 `TeamFileSystemHandler`（实现 `FileSystemHandler` 接口）」方案（参考 `FileShareDirBrowser`）；`sfc-common/components/common/index.ts` 新增一行导出 `FileExplorer`（此前未导出）；前端 typecheck 与 build-ext 重新验证通过（产物 20.34 kB，FileExplorer 正确外部化未被打包）。
