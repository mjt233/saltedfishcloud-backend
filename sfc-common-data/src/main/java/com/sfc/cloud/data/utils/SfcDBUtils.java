package com.sfc.cloud.data.utils;

import com.sfc.cloud.data.id.SfcIdUtil;
import com.sfc.cloud.data.identity.CurrentUserHolder;
import com.sfc.cloud.data.model.SfcAuditModel;
import com.sfc.cloud.data.tx.SfcTransactionManagerHolder;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 共享数据访问基座的数据库通用工具。
 *
 * <p>迁移自宿主的 {@code com.xiaotao.saltedfishcloud.utils.DBUtils}，并按「零宿主依赖」原则做了
 * <b>按需裁剪</b>：只保留全仓库确实在用、且不依赖宿主上下文的方法。</p>
 *
 * <p><b>已删除</b>：{@code isDBEmpty} / {@code isSQLiteDBEmpty} / {@code isMySQLDBEmpty}——
 * 全仓库无任何调用点。</p>
 *
 * <p><b>与宿主 db 语义的差异</b>：</p>
 * <ul>
 *   <li>批量插入时的当前用户取值由 {@code SecureUtils.getCurrentUid()} 改为
 *       {@link CurrentUserHolder#getCurrentUid()}；</li>
 *   <li>主键生成由 {@code IdUtil.getId()} 改为 {@link SfcIdUtil#getId()}；</li>
 *   <li>{@code executeWithTransactional} 三个重载所需的事务管理器由宿主
 *       {@code SpringContextUtils.getContext().getBean(PlatformTransactionManager.class)}
 *       改为 {@link SfcTransactionManagerHolder#getTransactionManager()}（静态方法无法依赖注入，
 *       故由各端启动时把实现注册进持有者）。</li>
 * </ul>
 *
 * <p><b>不重复造轮子</b>：实体表名、实体字段 getter 与字段扫描等反射能力统一由
 * {@link SfcClassUtils} 提供，本类不再内联等价实现。</p>
 *
 * @see SfcClassUtils
 * @see SfcTransactionManagerHolder
 */
public class SfcDBUtils {

    /**
     * 批量插入数据库数据
     * @param jdbcTemplate  jdbcTemplate
     * @param entityList    待插入的实体类
     * @param <T>           实体类型
     */
    public static <T extends SfcAuditModel> void batchInsert(JdbcTemplate jdbcTemplate, Iterable<T> entityList) {

        Objects.requireNonNull(entityList);
        Iterator<T> iterator = entityList.iterator();
        if (!iterator.hasNext()) {
            throw new IllegalArgumentException("entityList is empty");
        }

        List<T> entityBatchList = new ArrayList<>();
        T sample = iterator.next();
        entityBatchList.add(sample);
        List<SfcClassUtils.ColumnGetter> fields = SfcClassUtils.getClassEntityFieldGetter(sample.getClass());
        String tableName = SfcClassUtils.getEntityTableName(sample.getClass());

        // INSERT INTO xxxx (xx,xx,xx) VALUES (?,?,?),(?,?,?)
        StringBuilder sqlBuilder = new StringBuilder();
        Consumer<StringBuilder> buildInsert = sb ->
                sb.append("INSERT INTO `")
                        .append(tableName).append("` (")
                        .append(fields.stream().map(e -> "`" + e.columnName() + "`").collect(Collectors.joining(",")))
                        .append(") VALUES ");
        BiConsumer<StringBuilder, List<T>> buildStmt = (sb, tList) -> {
            // 构造 (?,?,?),(?,?,?)
            String stmt = fields.stream().map(e -> "?").collect(Collectors.joining(","));
            sb.append(tList.stream().map(t -> "(" + stmt + ")").collect(Collectors.joining(",")));
        };
        BiConsumer<String, List<T>> doInsert = (sql, batchEntityList) -> {
            jdbcTemplate.update(sql, ps -> {
                int idx = 1;
                try {
                    for (T entity : batchEntityList) {
                        if (entity.getId() == null) {
                            entity.setId(SfcIdUtil.getId());
                        }
                        if (entity.getCreateAt() == null) {
                            entity.setCreateAt(new Date());
                        }
                        if (entity.getUpdateAt() == null) {
                            entity.setUpdateAt(new Date());
                        }
                        if (entity.getUid() == null) {
                            entity.setUid(CurrentUserHolder.getCurrentUid());
                        }

                        for (SfcClassUtils.ColumnGetter field : fields) {
                            Object value = field.getter().invoke(entity);
                            if (value instanceof Enum<?> e) {
                                value = e.name();
                            }
                            ps.setObject(idx++, value);
                        }
                    }
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        };
        int batchSize = 1000 / fields.size();

        while (iterator.hasNext()) {
            entityBatchList.add(iterator.next());
            if (entityBatchList.size() >= batchSize) {
                buildInsert.accept(sqlBuilder);
                buildStmt.accept(sqlBuilder, entityBatchList);
                doInsert.accept(sqlBuilder.toString(), entityBatchList);
                sqlBuilder.setLength(0);
                entityBatchList.clear();
            }
        }
        if (!entityBatchList.isEmpty()) {
            buildInsert.accept(sqlBuilder);
            buildStmt.accept(sqlBuilder, entityBatchList);
            doInsert.accept(sqlBuilder.toString(), entityBatchList);
            sqlBuilder.setLength(0);
            entityBatchList.clear();
        }
    }

    /**
     * 获取数据库产品名，并统一转为小写，便于进行方言判断。
     * @param connection 数据库连接
     * @return 小写数据库产品名
     * @throws SQLException 当读取数据库元数据失败时抛出
     */
    @NotNull
    public static String getDatabaseProductName(Connection connection) throws SQLException {
        return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断当前数据库是否为SQLite。
     * @param connection 数据库连接
     * @return 若为SQLite则返回true
     * @throws SQLException 当读取数据库元数据失败时抛出
     */
    public static boolean isSQLite(Connection connection) throws SQLException {
        return "sqlite".equals(getDatabaseProductName(connection));
    }

    /**
     * 在新事务中执行Runnable。
     *
     * <p>迁移自宿主 {@code DBUtils.executeWithTransactional(int, Runnable)}，事务管理器取自
     * {@link SfcTransactionManagerHolder}（宿主原为 {@code SpringContextUtils} 的 Bean 查找）。
     * 传播行为、无返回值的执行回调与异常传播语义与宿主一致。</p>
     *
     * <p>与宿主的唯一写法差异：宿主用匿名类 {@code TransactionCallbackWithoutResult} 承载回调，
     * 该类自 Spring Framework 7.0 起已被 {@code @Deprecated}；此处改用语义等价的
     * {@link TransactionTemplate#executeWithoutResult(java.util.function.Consumer)}
     * （Spring 内部同样是包装成上述回调），以消除新增的弃用告警，运行行为不变。</p>
     *
     * @param propagationBehavior 事务传播行为，使用TransactionDefinition中的常量如PROPAGATION_REQUIRED等
     * @param runnable            要执行的任务
     * @see TransactionDefinition
     * @see SfcTransactionManagerHolder#getTransactionManager()
     */
    public static void executeWithTransactional(int propagationBehavior, Runnable runnable) {
        PlatformTransactionManager transactionManager = SfcTransactionManagerHolder.getTransactionManager();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(propagationBehavior);
        transactionTemplate.executeWithoutResult(status -> runnable.run());
    }

    /**
     * 在新事务中执行Callable并返回结果。
     *
     * <p>迁移自宿主 {@code DBUtils.executeWithTransactional(int, Callable)}：隔离级别固定为
     * {@link TransactionDefinition#ISOLATION_DEFAULT} 后委托给三参数重载，与宿主一致。</p>
     *
     * @param propagationBehavior 事务传播行为，使用TransactionDefinition中的常量如PROPAGATION_REQUIRED等
     * @param callable            要执行的任务，支持返回值
     * @param <T>                 返回值类型
     * @return 任务执行结果
     */
    public static <T> T executeWithTransactional(int propagationBehavior, Callable<T> callable) {
        return executeWithTransactional(propagationBehavior, TransactionDefinition.ISOLATION_DEFAULT, callable);
    }

    /**
     * 在新事务中执行Callable并返回结果。
     *
     * <p>迁移自宿主 {@code DBUtils.executeWithTransactional(int, int, Callable)}，事务管理器取自
     * {@link SfcTransactionManagerHolder}（宿主原为 {@code SpringContextUtils} 的 Bean 查找）。
     * 传播行为、隔离级别、返回值与异常传播语义与宿主一致：{@link RuntimeException} 原样抛出，
     * 受检异常包装为 {@link RuntimeException}。</p>
     *
     * @param propagationBehavior 事务传播行为，使用TransactionDefinition中的常量如PROPAGATION_REQUIRED等
     * @param isolationLevel      事务隔离级别 使用TransactionDefinition中的常量如ISOLATION_DEFAULT等
     * @param callable            要执行的任务，支持返回值
     * @param <T>                 返回值类型
     * @return 任务执行结果
     * @see SfcTransactionManagerHolder#getTransactionManager()
     */
    public static <T> T executeWithTransactional(int propagationBehavior, int isolationLevel, Callable<T> callable) {
        PlatformTransactionManager transactionManager = SfcTransactionManagerHolder.getTransactionManager();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(propagationBehavior);
        transactionTemplate.setIsolationLevel(isolationLevel);
        return transactionTemplate.execute(status -> {
            try {
                return callable.call();
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    /**
     * 判断数据表是否存在，兼容MySQL、SQLite、PostgreSQL
     * @param dataSource 数据源
     * @param tableName  表名（不区分大小写）
     * @return 是否存在
     */
    public static boolean tableExists(DataSource dataSource, String tableName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(conn.getCatalog(), conn.getSchema(), null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    if (rs.getString("TABLE_NAME").equalsIgnoreCase(tableName)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("检查数据表是否存在时出错: " + tableName, e);
        }
    }

    /**
     * 判断数据表中是否存在某个字段，兼容MySQL、SQLite、PostgreSQL
     * @param dataSource 数据源
     * @param tableName  表名（不区分大小写）
     * @param columnName 字段名（不区分大小写）
     * @return 是否存在
     */
    public static boolean columnExists(DataSource dataSource, String tableName, String columnName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), conn.getSchema(), tableName, columnName)) {
                while (rs.next()) {
                    if (rs.getString("TABLE_NAME").equalsIgnoreCase(tableName)
                            && rs.getString("COLUMN_NAME").equalsIgnoreCase(columnName)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("检查字段是否存在时出错: " + tableName + "." + columnName, e);
        }
    }

    /**
     * 判断某个数据表中是否存在指定索引，兼容MySQL、SQLite、PostgreSQL。
     * @param connection 数据库连接
     * @param tableName 表名（不区分大小写）
     * @param indexName 索引名（不区分大小写）
     * @return 是否存在
     */
    public static boolean indexExists(Connection connection, String tableName, String indexName) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getIndexInfo(connection.getCatalog(), connection.getSchema(), tableName, false, false)) {
                while (rs.next()) {
                    short type = rs.getShort("TYPE");
                    String currentIndexName = rs.getString("INDEX_NAME");
                    String currentTableName = rs.getString("TABLE_NAME");
                    if (type == DatabaseMetaData.tableIndexStatistic || currentIndexName == null || currentTableName == null) {
                        continue;
                    }
                    if (currentTableName.equalsIgnoreCase(tableName) && currentIndexName.equalsIgnoreCase(indexName)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("检查索引是否存在时出错: " + tableName + "." + indexName, e);
        }
    }

    /**
     * 删除指定数据表上的索引，兼容MySQL、SQLite、PostgreSQL。
     * @param connection 数据库连接
     * @param tableName 表名
     * @param indexName 索引名
     */
    public static void dropIndex(Connection connection, String tableName, String indexName) {
        try (Statement statement = connection.createStatement()) {
            String dbType = getDatabaseProductName(connection);
            String quotedTableName = quoteIdentifier(connection, tableName);
            String quotedIndexName = quoteIdentifier(connection, indexName);
            String sql;
            if (dbType.contains("mysql") || dbType.contains("mariadb")) {
                sql = "ALTER TABLE " + quotedTableName + " DROP INDEX " + quotedIndexName;
            } else if (dbType.contains("sqlite") || dbType.contains("postgre")) {
                sql = "DROP INDEX IF EXISTS " + quotedIndexName;
            } else {
                throw new IllegalArgumentException("不支持删除索引的数据库类型：" + dbType);
            }
            statement.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("删除索引时出错: " + tableName + "." + indexName, e);
        }
    }

    /**
     * 使用数据库方言对应的引用符包装标识符，避免key/value等保留关键字导致SQL执行失败。
     *
     * @param connection 数据库连接
     * @param identifier 待包装的标识符
     * @return 已用方言引用符包装的标识符
     * @throws SQLException 当读取数据库元数据失败时抛出
     */
    @NotNull
    public static String quoteIdentifier(Connection connection, String identifier) throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            String dbType = getDatabaseProductName(connection);
            if (dbType.contains("mysql") || dbType.contains("mariadb")) {
                quote = "`";
            } else if (dbType.contains("sqlite") || dbType.contains("postgre")) {
                quote = "\"";
            } else {
                return identifier;
            }
        }
        return quote + identifier.replace(quote, quote + quote) + quote;
    }
}
