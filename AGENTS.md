# 咸鱼云网盘 AI 编程说明

## 项目说明

这是一个基于 SpringBoot 的网盘项目

## 核心技术栈

- SpringBoot
- JPA
- Redis
- MySQL

## 代码规范

- 文档化：所有新增方法和字段必须添加 JavaDoc 文档注释
- 分页查询：controller 和 service 层涉及分页查询数据的，统一使用`CommonPageInfo`(com.xiaotao.saltedfishcloud.model.CommonPageInfo)封装。
- JPA Repository层分页查询必须使用`Pageable`参数，返回值为`Page`(org.springframework.data.domain.Page)。
- 异常处理：优先抛出业务自定义异常（JsonException），由全局异常处理器拦截。

## 权限与安全

### 网盘数据安全

| 资源类型 | 用户 ID (UID) | 读取权限            | 写入权限         |
|------|-------------|-----------------|--------------|
| 公共资源 | 0           | 所有人 (匿名/用户/管理员) | 仅管理员 (ADMIN) |
| 私人资源 | > 0         | 资源所有者 或 管理员     | 资源所有者 或 管理员  |

### Controller 鉴权注解

业务层面分3个权限等级：

- `@AllowAnonymous`: 允许免登录访问。
- `@RolesAllowed(SysRole.ADMIN)`: 仅限管理员。
- 默认状态: 所有未标注上述注解的方法，默认要求用户登录

#### UID 安全校验

- 直接参数：Controller 方法中所有 `Long uid` 或 `Integer uid` 参数必须标注 `@UID`。
- 对象封装：若参数为 DTO/Entity 对象且包含 uid 字段，必须在调用 Service 前执行：
  ```java
    UIDValidator.validate(dto.getUid());
  ```

## 工作流与验证机制

### 编译验证

- 使用MCP的build_project对代码进行编译验证，避免使用命令行执行mvn

### 单元测试原则

- 静默原则：除非用户明确要求（例如：“请为该功能编写测试”），否则严禁自动生成测试类。

### 检查清单

- 是否遗漏了 @UID 注解？
- 公共资源（uid=0）的写入逻辑是否做了管理员校验？
- 是否已经通过 build_project 验证？
- JavaDoc 是否已补全？

