            ---
            name: jpa-repo-generator
            title: jpa-repo-generator
            description: 创建一个JPA仓库接口
            ---

            # jpa-repo-generator

Repository的数据实体类Entity需要满足以下条件：
- 继承`com.xiaotao.saltedfishcloud.model.template.AuditModel`


生成的Repository接口规则如下:
1. 命名为{数据实体类Entity}Repo，如实体类为LogRecord，生成的Repository接口则为LogRecordRepo。
2. 需要继承`com.xiaotao.saltedfishcloud.dao.BaseRepo`，示例如下:

   ```java
   package com.xiaotao.saltedfishcloud.dao.jpa;
   
   import com.xiaotao.saltedfishcloud.dao.BaseRepo;
   import com.xiaotao.saltedfishcloud.model.po.LogRecord;
   
   import java.util.Date;
   import java.util.List;
   
   public interface LogRecordRepo extends BaseRepo<LogRecord> {
   
   
   }
   
   ```
3. 保存位置遵循以下规则:
   - 如果实体类的所在的maven模块是在sfc-api下的，则生成到sfc-api/src/main/java/com/xiaotao/saltedfishcloud/dao/jpa
      如：
   - 如果实体类的所在的maven模块不在sfc-api下，则生成到与实体类相同的maven模块目录下，包位置则取实体类所在的package中名为`model`的package同级的名为`repo`下  
      如: 实体类 `com.sfc.webshell.model.po.ShellExecuteRecord` 对应的Repository则为 `com.sfc.webshell.repo.ShellExecuteRecordRepo`