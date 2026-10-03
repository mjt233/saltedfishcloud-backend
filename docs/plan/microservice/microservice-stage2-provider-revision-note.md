# 微服务改造 · 阶段 2 provider 修订说明

> **性质**：决策修订记录（**已生效**，2026-10-03）
> **结论**：阶段 2 的 provider 由"基于 `CacheService` 的独立实现"改为"**纯内存实现，且不依赖任何系统既有组件**"
> **前置事实（已核对源码）**：阶段 2 尚未实施（`sfc-core` 无任何 SPI 实现，`SysProperties.Service` 无注册字段，三个配置文件无 `registry-provider`），故本次修订**零返工**
> **修订序列**：包装 `ClusterService` → 独立 `cache` 实现 → **纯内存实现**（后两次同日）
> **已应用**：[档位 1 设计](microservice-phase1-design.md) · [路线图](microservice-roadmap.md) · [评审报告](review/microservice-plan-review.md) · [档位 3 设计](microservice-phase3-frontend-integration.md) · [依赖冲突调试手册](microservice-nacos-dependency-debugging.md)

---

## 1. 核心规则

> **未使用 Nacos ⇒ 未启用微服务模式 ⇒ 系统处于完整单体 / 集群状态。此状态下的服务注册关系只在进程内维护，完全不考虑跨进程。**

| # | 推论 |
|---|---|
| 1 | 内存注册表**不承担"发现"职责** —— 未启用微服务模式时不存在异构服务，没有可发现的对象 |
| 2 | 跨进程可见性、TTL / 续约、死实例清理、多节点视图**全部不属于** standalone 的职责；**它们正是 Nacos 存在的意义** |
| 3 | standalone 下「服务列表」是**进程内视图**，只返回自身；集群节点仍由 `GET /api/cluster/listNodes` 回答。两者**职责分离，不合并** |

---

## 2. 阶段 2 实现规格

### 2.1 硬约束

| # | 约束 | 收益 |
|---|---|---|
| 1 | **不依赖任何系统既有组件**：不注入 `CacheService` / `MQService`、不包装 `ClusterService`，只用一个自持 `Map` | 与 `cache-provider: redis\|local` 完全解耦；不写 Redis、不新增定时任务 |
| 2 | **不实现任何跨进程语义**：无 TTL、无续约、无二级索引、无惰性清理 | 原方案最绕的部分整体消失 |
| 3 | **质量底线**：`register()` 的参数必须真实入库、`deregister()` 只删自己 | 不得重演原方案 A"参数被静默忽略、接口是假的" |

> 决策 5 否掉「包装 `ClusterService`」的核心理由由此简化为一条：**那正是"依赖系统既有组件"**。

### 2.2 四项已确认细节

| # | 决策 | 理由 | 代价 |
|---|---|---|---|
| 1 | `group` **参与**键结构，发现侧 group 取配置 | 保住"发现侧没有 group 参数"这条契约认知，提前暴露 Nacos `DEFAULT_GROUP` 错配坑 | 约 10 行 |
| 2 | **单类**同时实现两个 SPI | 一个文件、一个 Bean，注册与发现共享同一 Map | 与 Nacos 侧两个类形态不对称 |
| 3 | **保留** `ServiceIdentityResolver` | Nacos 模式同样需要"调用方解析自身身份"；standalone 下可预检地址不可达（原风险 3） | 注册时机须等到 `ApplicationReadyEvent` |
| 4 | **保留** `GET /api/admin/sys/service/list` | 阶段 2 唯一可验证交付物 | standalone 下信息量低 |

键结构（`namespace` **不参与** —— 内存 Map 即单一命名空间，刻意如此）：

```text
{group}:{serviceName}   →   Map<instanceId, ServiceInstance>
```

### 2.3 两处命名（按既有惯例采用）

| 项 | 取值 | 依据 |
|---|---|---|
| `registry-provider` 默认值 | `local` | 对齐 `cache-provider` / `lock-provider` / `mq-provider` 的 `local` 家族（语义：仅当前实例内生效）。⚠️ 不再用 `cache` —— 该取值描述的是 `CacheService` 后端 |
| 实现类名 | `LocalServiceRegistry` | 同上 |

> **顺带修正**：原默认值 `cache` 在开发配置（`cache-provider: local`）下跑的本来就是内存实现，改名后取值与实现首次一致。

---

## 3. 与原方案的差异

| 原计划 | 修订后 |
|---|---|
| `CacheServiceRegistry`：实例键 + 两级 Set 索引 + 5s 续约 + 10s TTL + `DisposableBean` 注销 | **删除**，由一个 Map 承载 |
| `CacheServiceDiscovery`：`sMembers` + 惰性清理 | **删除**，并入同一类 |
| `CacheKeyPrefixes.CLOUD`（路线图 1.3） | **删除**（键空间不再存在） |
| 决策 5 代价一 / 风险 5：Redis 两套注册表 | **两种模式都不存在** |
| 风险 7：Set 成员不随 TTL 过期消失 | **删除** |
| 生产常驻开销（第二个 5s 定时器 + 每 5s 一组 Redis 写） | **消失** |
| 决策 6：不要只靠 `Closeable.close()` 注销 | **仅对 Nacos provider 生效** |
| 阶段 2 规模「中」 | 「**小**」（4 个新文件，约 200 行） |
| 验证项"手工塞入第二服务名 → 见两个服务" | **废弃**（见 §5.2） |

> **原方案的"跨进程"能力本就无消费者**：能写 `sfc:cloud:*` 的只有另一个 `sfc-core` 节点，而它已由 `ClusterService` 覆盖；demo 微服务按设计不依赖咸鱼云任何模块，永远不会写这个键空间。此外两级索引键在原方案中**无 TTL**（`sAdd` 对新键走 `NEVER_EXPIRE_AT`），开发模式下会落盘 `./localcache.data` 并跨重启存活，Redis 模式下永久常驻，且服务名总索引没有清理路径。

---

## 4. 被推翻 / 已改写的旧论据

| 旧论据 | 处理 |
|---|---|
| 「纯内存」被列为**否决**方案 C，理由："无法跨进程发现，'发现'这件事根本没被验证" | **前提已推翻**：验证"发现"是 Nacos 的职责，不是 standalone 的职责 |
| 阶段 2 排序理由第 1 条"建立可验证的发现能力" | 改写为：① 给 standalone 一个合法 provider（否则 `registry-provider: nacos` 且未装插件时无 Bean）；② 提前验证接线、身份解析与消费者注入路径，使阶段 3 只剩"依赖冲突"一个变量 |
| 「代价之二：排查面不再严格收敛到 1」 | **恢复成立**（接线已验证）；仅丢失"一个非平凡后端能兑现契约"这一验证项 |
| 「cluster vs cloud 两套注册表」对照教学、学习点"TTL 与二级索引一致性" | 前者取消；后者改为**源码研读项**（教材：`ClusterServiceImpl` + `ClusterHoldSchedule`） |
| §1 目标第 3 条"两个 provider 可互换" | 改写：SPI 的价值是**依赖隔离**（core 不依赖 `nacos-client`），而非 provider 互换 |

---

## 5. 代价与验证标准

### 5.1 代价

**`ServiceDiscovery` 中"可能返回多个服务 / 多个实例"这一半，在阶段 4 之前始终是未执行的代码**（Map 里能放，但没有注册者）。原验证手段失效，缓解方式是接受它 —— 多服务名路径由阶段 4 的 demo 微服务首次真正走通。

> 因此"阶段 2 从闭环降级为桩"的说法是准确的：它是**接线桩 + 身份解析桩**，不是发现能力桩。

### 5.2 阶段 2 验证标准

- [ ] `registry-provider: local` 启动 → **现有功能零回归**（`GET /api/cluster/listNodes` 照常）
- [ ] `GET /api/admin/sys/service/list` 返回**自身 1 条**，host / port 与预期一致
- [ ] `server.port: 0` 时显示**真实**绑定端口（身份解析证明，也是阶段 3 风险 3 的预检）
- [ ] ~~手工塞入第二服务名~~（**废弃**）

---

## 6. 实施轮廓

| 文件 | 职责 | 估行数 |
|---|---|---|
| `LocalServiceRegistry` | 单类实现两个 SPI；`ConcurrentHashMap<{group}:{serviceName}, Map<instanceId, ServiceInstance>>`；`isAvailable()` 恒 `true`；`close()` 清 Map | ~60 |
| `ServiceIdentityResolver` | `advertise-address` 优先；`server.port`，为 `0` 时读 web 容器真实端口；`instanceId = {host}:{port}` | ~40 |
| `ServiceRegistryInitializer` | `ApplicationReadyEvent` → `register(definition, instance)` | ~40 |
| `ServiceRegistryController` | `PREFIX = "/api/admin/sys/service"`，类级 `@RolesAllowed({"ADMIN"})`，`GET /list` | ~40 |

装配与实现要点：

1. `@ConditionalOnProperty(..., havingValue = "local", matchIfMissing = true)` —— **`matchIfMissing` 只能出现在 local 一侧**，否则与 Nacos 的 Bean 同时命中。
2. **`@Autowired ServiceRegistry` 是安全的**：插件 Bean 由 `AutoConfiguration.imports` 注册、晚于 `@Component` 扫描，这会让 `@ConditionalOnBean` 之类的**条件评估**看不到它，但**不影响依赖注入**。这正是"core 常驻 `local` provider"优于"core 无 provider"的原因（后者会迫使消费者改用 `ObjectProvider` 判空）。
3. **可选 fail-fast（约 10 行）**：`ServiceRegistryInitializer` 注入 `ObjectProvider<ServiceRegistry>`，当 `registry-provider=nacos` 而取不到 Bean 时抛出明确提示，替代 `NoSuchBeanDefinitionException`。

---

## 7. 已应用的文档改动

| 文档 | 改动 |
|---|---|
| 档位 1 设计 | 头部与目标（依赖隔离）、拓扑、§3 概念对照表、§4.3 / §4.5、决策 4 / 5 / 6、Step 3 / 4、删除风险 5 与 7、学习点改写 |
| 路线图 | 阶段 1.3（删 `CLOUD`）、阶段 2 收缩为 7 项并改写排序理由、总览表、§五 错误 4、§六 规模感知 |
| 评审报告 | 顶部修订影响说明更新（含 `provider: cluster` → `local` 陈旧引用修正）、§4.2、§5.1、§5.2 附注、§7 承重假设 4 降级、§8 行动项 |
| 档位 3 设计 | §12 Step 1 取值改 `local`；§5.3 补"未启用微服务模式时清单必然为空" |
| 依赖冲突调试手册 | §7.1 与 §8 的 `registry-provider: cache` → `local`（原修订清单遗漏，已补） |

> **仍未决策一项**：是否以"未启用微服务模式"显式门控 `/api/ext/**`（standalone 下代理 Controller 可不注册，省掉 503 / 404 降级分支）。建议阶段 7 / 8 启动前再定。

---

## 8. 复核清单（实施阶段 2 时逐条确认）

- [ ] `LocalServiceRegistry` **未注入**任何既有组件
- [ ] `register()` 参数真实入库；`deregister()` 只删自身
- [ ] `group` 参与键结构；`namespace` 不参与，且已写明是刻意的
- [ ] `matchIfMissing = true` 只在 local 一侧
- [ ] `ServiceRegistryInitializer` 监听 `ApplicationReadyEvent`
- [ ] 管理端 API：`@RolesAllowed({"ADMIN"})`、`@RequestParam`、无 `{}` 占位符
- [ ] 改动 `pom.xml` → 用系统 `mvn` 验证；不生成单元测试；JavaDoc 补全
