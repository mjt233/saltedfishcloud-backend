package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.constant.SysConfigName;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.utils.DBUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 数据迁移与版本检查器<br>
 * 确保在数据库连接初始化之后、JPA初始化之前执行数据迁移和版本检查，防止因字段变更导致的数据不完整，并阻止不受支持的跨版本升级。
 */
@Slf4j
public class DataMigrateAndCheckInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    // -------------------------------------------------------------------------
    // Config表字段迁移：key -> item_key, value -> item_value
    // -------------------------------------------------------------------------

    /**
     * 在DataSource可用后立即执行Config表字段迁移，确保在JPA及版本检查之前完成。
     */
    private void migrateConfigTable(DataSource dataSource) {
        if (!DBUtils.tableExists(dataSource, "config")) {
            log.debug("[ConfigFieldMigration] config表不存在，无需迁移");
            return;
        }
        if (DBUtils.columnExists(dataSource, "config", "item_key")) {
            log.debug("[ConfigFieldMigration] config表已包含item_key字段，无需迁移");
            return;
        }
        log.info("[ConfigFieldMigration] 开始迁移config表字段: key -> item_key, value -> item_value");
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE config RENAME COLUMN " + DBUtils.quoteIdentifier(conn, "key") + " TO item_key");
                stmt.execute("ALTER TABLE config RENAME COLUMN " + DBUtils.quoteIdentifier(conn, "value") + " TO item_value");
            }
            log.info("[ConfigFieldMigration] config表字段迁移完成");
        } catch (SQLException e) {
            throw new RuntimeException("迁移config表字段时出错", e);
        }
    }

    // -------------------------------------------------------------------------
    // 版本检查
    // -------------------------------------------------------------------------

    /**
     * 检查系统是否满足跨版本升级条件。防止不支持从太早远的版本中进行数据迁移。
     * @param dataSource    数据库连接dataSource
     */
    private void checkSystemVersion(DataSource dataSource) {
        if(!DBUtils.tableExists(dataSource, "config")) {
            return;
        }
        String version = getVersionStr(dataSource);
        if (version != null && Version.valueOf(version).isLessThen(Version.valueOf("2.8.0"))) {
            throw new IllegalArgumentException("上次运行的程序版本低于为" + version + "，低于2.8.0的版本无法直接升级到当前版本。请先升级到2.8.0，升级完成后再升级该版本。");
        }
    }

    /**
     * 获取上次系统启动时的版本号字符串
     * @param dataSource    数据库连接dataSource
     * @return  上次启动的系统版本字符串，如果无法获取（如表或字段不存在）则返回null
     */
    @Nullable
    private static String getVersionStr(DataSource dataSource) {
        boolean hasNewKeyField = DBUtils.columnExists(dataSource, "config", "item_key");
        boolean hasNewValueField = DBUtils.columnExists(dataSource, "config", "item_value");

        String valueField = hasNewValueField && hasNewKeyField ? "item_value" : "value";
        String keyField = hasNewValueField && hasNewKeyField ? "item_key" : "key";

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String sql = buildConfigVersionQuerySql(dataSource, valueField, keyField);
        List<String> res = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), SysConfigName.Common.VERSION);
        String version;
        if (!res.isEmpty()) {
            version = res.get(0);
        } else {
            res = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), SysConfigName.OLD_VERSION);
            if (!res.isEmpty()) {
                version = res.get(0);
            } else {
                version = null;
            }
        }
        return version;
    }

    /**
     * 构造config表版本查询SQL，对表名和字段名进行安全引用，兼容MySQL、SQLite、PostgreSQL。
     */
    @NotNull
    private static String buildConfigVersionQuerySql(DataSource dataSource, String valueField, String keyField) {
        try (Connection connection = dataSource.getConnection()) {
            String quotedTableName = DBUtils.quoteIdentifier(connection, "config");
            String quotedValueField = DBUtils.quoteIdentifier(connection, valueField);
            String quotedKeyField = DBUtils.quoteIdentifier(connection, keyField);
            return String.format("SELECT %s FROM %s WHERE %s = ?", quotedValueField, quotedTableName, quotedKeyField);
        } catch (SQLException e) {
            throw new RuntimeException("构造config表版本查询SQL时出错", e);
        }
    }

    /**
     * 执行数据迁移和系统版本检查
     * @param dataSource 数据库连接 DataSource
     */
    private void doMigrateAndCheckVersion(DataSource dataSource) {

        // 执行版本检查
        checkSystemVersion(dataSource);

        // 执行字段迁移，确保后续操作使用新字段名
        migrateConfigTable(dataSource);

    }

    @Override
    public void initialize(@NotNull ConfigurableApplicationContext applicationContext) {
        applicationContext.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
                if (!(bean instanceof DataSource dataSource)) {
                    return bean;
                }
                doMigrateAndCheckVersion(dataSource);
                return bean;
            }
        });
    }
}
