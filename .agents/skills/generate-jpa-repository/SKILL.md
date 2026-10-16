---
name: generate-jpa-repository
description: 实体类创建一个对应的JPA Repository接口 
---


Repository的数据实体类Entity需要满足以下条件：
- 继承`com.sfc.cloud.data.model.SfcAuditModel`（Phase 1 起，实体基类已从 `sfc-api` 迁至共享库 `sfc-common-data`；旧名 `com.xiaotao.saltedfishcloud.model.template.AuditModel` 已不存在）
- JPA Entity如果字段用到Enum类型，必须添加`@Enumerated(EnumType.STRING)`注解
- JPA Entity索引名称需要符合以下格式`idx_[表名]_[列1名]_[列2名]......`
- JPA Entity实体类字段的`@Column`注解 与 `@Table`注解都避免配置`name`属性。


生成的Repository接口规则如下:
1. 命名为{数据实体类Entity}Repo，如实体类为LogRecord，生成的Repository接口则为LogRecordRepo。
2. 需要继承`com.sfc.cloud.data.repo.SfcBaseRepo`（Phase 1 起由 `sfc-common-data` 提供；旧名 `com.xiaotao.saltedfishcloud.dao.BaseRepo` 已不存在），示例如下:

   ```java
   package com.xiaotao.saltedfishcloud.dao.jpa;
   
   import com.sfc.cloud.data.repo.SfcBaseRepo;
   import com.xiaotao.saltedfishcloud.model.po.LogRecord;
   
   import java.util.Date;
   import java.util.List;
   
   public interface LogRecordRepo extends SfcBaseRepo<LogRecord> {
   
   
   }
   
   ```
3. 保存位置遵循以下规则:
    - 如果实体类的所在的maven模块是在sfc-api下的，则生成到sfc-api/src/main/java/com/xiaotao/saltedfishcloud/dao/jpa
      如：
    - 如果实体类的所在的maven模块不在sfc-api下，则生成到与实体类相同的maven模块目录下，包位置则取实体类所在的package中名为`model`的package同级的名为`repo`下  
      如: 实体类 `com.sfc.webshell.model.po.ShellExecuteRecord` 对应的Repository则为 `com.sfc.webshell.repo.ShellExecuteRecordRepo`