package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.annotations.update.RollbackAction;
import com.xiaotao.saltedfishcloud.annotations.update.UpdateAction;
import com.xiaotao.saltedfishcloud.annotations.update.Updater;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileInfoService;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统核心模块版本更新处理器
 */
@Component
@Updater
@Slf4j
public class SystemUpdater {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private FileInfoService fileInfoService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private void executeSqlResource(String sqlFileClassPath) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(sqlFileClassPath));
        }

    }

    @RollbackAction("2.7.0")
    public void rollback2_7_0() throws SQLException {
        this.executeSqlResource("sql/2.7.0.rollback.no-auto.sql");
        log.warn("2.7.0更新出错，但是表结构回滚成功");
    }

    @UpdateAction("2.7.0")
    public void update2_7_0() throws SQLException {
        // 先执行修改表结构的脚本
        long begin = System.currentTimeMillis();
        log.info("======== 开始执行2.7.0 文件表file_table 表结构更新 ========");
        log.info("======== 执行结构调整脚本... ========");
        this.executeSqlResource("sql/2.7.0.no-auto.sql");

        log.info("======== 查询原数据... ========");
        List<FileInfo> fileInfoList = jdbcTemplate.queryForList("SELECT * FROM file_table")
                .stream()
                .map(mapRes -> {
                    HashMap<String, Object> newMap = new HashMap<>();
                    mapRes.forEach((field, val) -> newMap.put(StringUtils.underToCamel(field), val));
                    return ObjectUtils.mapToBean(newMap, FileInfo.class);
                })
                .collect(Collectors.toList());
        log.info("======== 共{}条数据 ========", fileInfoList.size());

        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        TransactionStatus transaction = transactionManager.getTransaction(transactionDefinition);

        try {
            // 给文件列表数据赋值ID
            log.info("======== 清空原数据... ========");
            jdbcTemplate.execute("DELETE FROM file_table");
            log.info("======== 更新新数据... ========");
            fileInfoService.batchInsert(fileInfoList);
            log.info("======== 提交事务... ========");
            transactionManager.commit(transaction);

            // 设置主键字段
            log.info("======== 设置主键... ========");
            jdbcTemplate.execute("ALTER TABLE file_table ADD PRIMARY KEY (`id`)");
            log.info("======== 更新完成，共{}条数据，耗时：{}ms ========", fileInfoList.size(), System.currentTimeMillis() - begin);
        } catch (Throwable err) {
            try {
                transactionManager.rollback(transaction);
            } catch (Throwable ignore) {}
            log.error("file_table表结构更新出错: ", err);
            throw err;
        }
    }

    @UpdateAction("2.6.6")
    public void update2_6_6() {
        /*
            2.6.6版本更新主要内容：
            代理信息配置支持用户自行配置代理节点和连通性测试URL
         */

        // 先对新增字段，并赋值初始值
        Date now = new Date();
        jdbcTemplate.execute(
                "ALTER TABLE `proxy` ADD COLUMN `id`  bigint NOT NULL COMMENT '主键' FIRST," +
                "ADD COLUMN `uid`  bigint NULL DEFAULT 0 COMMENT '创建用户id'," +
                "ADD COLUMN `test_url`  varchar(255) NULL," +
                "ADD COLUMN `create_at`  datetime NULL," +
                "ADD COLUMN `update_at`  datetime NULL"
        );
        jdbcTemplate.query("SELECT * FROM proxy", new BeanPropertyRowMapper<>(ProxyInfo.class))
                .forEach(proxyInfo -> jdbcTemplate.update(
                        "UPDATE proxy SET id = ?, uid = 0, create_at = ?, update_at = ? WHERE name = ?",
                        IdUtil.getId(), now, now, proxyInfo.getName())
                );

        // 重新构建主键字段
        jdbcTemplate.execute("ALTER TABLE `proxy` DROP PRIMARY KEY, ADD PRIMARY KEY (`id`)");

        // 构建索引
        jdbcTemplate.execute("ALTER TABLE `proxy` ADD INDEX `i_uid` (`uid`, `name`) USING BTREE");

    }
}
