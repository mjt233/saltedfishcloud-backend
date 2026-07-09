# SpringBoot 4.1.0 升级——人工验证清单

> 生成日期：2026-07-08  
> 分支：upgrade-springboot-4.1  
> 编译状态：✅ 通过（仅弃用警告）  
> 启动验证：✅ 通过（主模块 + local-mq + data-manager，SQLite 本地模式）

## 已完成的自动化验证

| 项目 | 状态 | 备注 |
|---|---|---|
| 全模块编译（37/37） | ✅ | mvn clean compile -T 32 |
| 主模块 + local-mq 启动 | ✅ | 16.7s，SQLite |
| + data-manager 插件启动 | ✅ | Groovy 4.x 环境正常 |

## 需人工验证的项目

### 1. MySQL + Redis 完整环境启动

需使用 product profile 或 develop-mysql profile 验证：
- MySQL 数据库连接与 JPA DDL（`ddl-auto: update`）
- Redis 连接与 Redisson 4.6.1 分布式锁
- `sys.redis.enabled: true` 下的缓存、MQ、RPC 全链路

```bash
# 建议使用 develop-mysql profile
mvn spring-boot:run -pl sfc-core -Dspring-boot.run.profiles=develop-mysql
```

> ⚠️ 需先确保 MySQL 中无旧版本 schema 冲突（建议备份后重建空库）

---

### 2. OIDC 授权服务器全链路回归

启动后设置 `sys.oidc.enabled=true`，验证以下端点：

| 端点 | 操作 |
|---|---|
| `/.well-known/openid-configuration` | 确认 OIDC 元数据返回正常 |
| `/oauth2/authorize` | 授权码流程：跳转登录 → consent → code |
| `/oauth2/token` | code 换 token |
| `/oauth2/jwks` | JWK Set 正常返回 |
| `/oauth2/userinfo` | UserInfo 返回自定义 claims |
| `/oauth2/device_authorization` | 设备授权流程（如有使用） |
| `/api/openApi/**` | 资源服务器 Bearer Token 鉴权 |

> ⚠️ Security 7 中 `OAuth2AuthorizationServerConfigurer` API 变化较大，constructor 从静态工厂改为 `new`，需确认所有端点配置生效

---

### 3. swagger 注解全量迁移验证

18 个文件的 98 处注解已从 `io.swagger.annotations` 迁移至 `io.swagger.v3.oas.annotations`，需人工验证：

- [ ] 访问 `/swagger-ui.html`，确认所有 API 分组正常展示
- [ ] 访问 `/v3/api-docs`，确认 JSON 文档完整
- [ ] 逐分组检查参数说明、返回值描述是否与原 Swagger 2 一致
- [ ] `@ApiModelProperty(value="xxx")` → `@Schema(description="xxx")` 语义验证
- [ ] `@ApiParam` → `@Parameter` 的 `required`、`example` 属性迁移验证

---

### 4. JWT Token 签发与鉴权

`JwtUtils.java` 已从 jjwt 0.9.0 API 迁移至 0.12.6：

- [ ] 用户登录：签发 Token，验证过期时间、claims 内容
- [ ] Token 鉴权：携带 JWT 访问需登录接口，401/403 行为正确
- [ ] Token 过期：超时后返回预期错误码
- [ ] Token 续期：自动刷新机制是否正常

> ⚠️ jjwt 0.12.x 加密生成逻辑变更（`signWith` → `signWith(key)`），需确认 Token 格式不变或迁移过渡期允许双格式

---

### 5. Jackson Long→String 序列化器（**已知缺口**）

`JacksonConfig` 已迁移至 `JsonMapperBuilderCustomizer`（Jackson 3），但 Long/long → String 全局序列化器**未完成迁移**。

| 项 | 变化 |
|---|---|
| `FAIL_ON_EMPTY_BEANS` | ✅ 已迁移 |
| `Long.TYPE → String` 序列化 | ❌ 未迁移（Jackson 3 Builder 无 `addSerializer` 方法） |
| `Long.class → String` 序列化 | ❌ 同上 |

**影响**：前端 API 返回的 `id`/`uid` 字段若为 Long 类型且超出 JS 安全整数范围，将出现精度丢失。

**建议方案**：
1. 使用 `@JacksonComponent` 注解定义全局 Long 序列化器（Spring Boot 4 已从 `@JsonComponent` 重命名）
2. 或在实体类的 id 字段上添加 `@JsonSerialize(using = ToStringSerializer.class)`（逐字段控制）

---

### 6. Redisson 自动配置类名验证

`LockProviderAutoConfigurationImportFilter.java` 通过类名字符串引用 Redisson 的自动配置类：
- `RedissonAutoConfiguration`
- `RedissonAutoConfigurationV2`

Redisson 4.6.1 的包结构和类名可能变化，需在 Redis 启用时验证：

- [ ] `sys.service.lock-provider: redisson` 时，Redisson 自动配置正确加载
- [ ] `sys.service.lock-provider: local` 时，Redisson 自动配置正确排除
- [ ] 分布式锁（`@Lock` 注解）功能正常

---

### 7. Spring Session Redis（如有使用）

4.0 属性重命名 `spring.session.redis.*` → `spring.session.data.redis.*`，需确认：
- [ ] 如使用 Redis Session，配置文件中的属性名已更新
- [ ] Session 共享功能正常（多节点测试）

---

### 8. Spring AI 插件兼容性

以下模块的 Spring AI 版本需确认与 SpringBoot 4.1 兼容：

| 模块 | 当前版本 | 备注 |
|---|---|---|
| sfc-ext-mcp | spring-ai 1.1.6 | 需确认是否支持 Boot 4.1 |
| sfc-ext-ai | spring-ai 2.0.0 | 与 mcp 版本不一致 |

- [ ] 加载 sfc-ext-mcp 插件，验证 MCP Server 端点正常
- [ ] 加载 sfc-ext-ai 插件，验证 OpenAI 聊天功能正常
- [ ] 统一两个模块的 spring-ai 版本

---

### 9. 其他扩展插件逐个验证

以下插件可逐步启用 `application-develop.yml` 中的 `plugin.extra-resource` 配置项，逐一验证：

| 优先级 | 插件 | 验证要点 |
|---|---|---|
| 高 | sfc-ext-webdav | 嵌入式 Tomcat 正常启动，WebDAV 客户端连接 |
| 高 | sfc-ext-download | 离线下载任务创建、执行、中断 |
| 高 | sfc-ext-mcp | MCP Server 端点可用（需先处理 Spring AI 版本） |
| 高 | sfc-ext-webrtc | WebRTC 信令 WebSocket |
| 高 | sfc-ext-web-shell | WebShell WebSocket 端点 |
| 中 | sfc-ext-video-enhance | 视频转码任务 |
| 中 | sfc-ext-minio-store | MinIO 存储挂载 |
| 中 | sfc-ext-samba-store | Samba 存储挂载 |
| 中 | sfc-ext-sftp-store | SFTP 存储挂载 |
| 中 | sfc-ext-ftp-store | FTP 存储挂载 |
| 中 | sfc-ext-oss-store | OSS/S3 存储挂载 |
| 低 | sfc-ext-only-office | OnlyOffice 集成 |
| 低 | sfc-ext-apk-parser | APK 解析 |
| 低 | sfc-ext-static-publish | 静态页面发布 |
| 低 | sfc-ext-quick-share | 快速分享 |
| 低 | sfc-ext-pxe-boot | PXE 网络启动 |
| 低 | sfc-ext-network-tools | 网络工具 |
| 低 | sfc-ext-ftp-server | 嵌入式 FTP 服务器 |
| 低 | sfc-ext-music | 音乐播放 |
| 低 | sfc-ext-webdav-store | WebDAV 存储挂载 |

---

### 10. 配置属性迁移收尾

| 操作 | 说明 |
|---|---|
| 移除 `spring-boot-properties-migrator` 依赖 | 启动验证通过后即可从 `pom.xml` 删除 |
| 检查启动日志中的 properties-migrator 诊断 | 确认无遗漏的属性重命名 |

---

### 11. 弃用 API 替换

| 弃用 API | 影响范围 | 优先级 |
|---|---|---|
| `@org.springframework.lang.Nullable` | sfc-core/sfc-api 多处 | 低（JSpecify 替代） |
| `PathResource` (forRemoval) | sfc-core/sfc-api/多个插件 | 中 |
| `Jackson2JsonRedisSerializer` (forRemoval) | sfc-rpc, sfc-core | 中 |
| `configureMessageConverters` (forRemoval) | SpringConfig.java | 中 |
| `SecurityJackson2Modules` (forRemoval) | JpaOAuth2AuthorizationService.java | 中 |

---

## 验证通过标准

升级可视为完成，当满足以下条件：

- [ ] MySQL + Redis 完整环境中启动无 ERROR
- [ ] 用户登录鉴权流程回归通过
- [ ] 文件上传/下载/复制/移动 基本操作正常
- [ ] swagger-ui 文档展示完整
- [ ] 已移除 `spring-boot-properties-migrator` 依赖
- [ ] 核心扩展插件（webdav/download/mcp/webrtc/webshell）逐个验证
