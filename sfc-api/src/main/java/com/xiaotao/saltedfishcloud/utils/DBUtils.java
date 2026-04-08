package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.util.function.Tuple2;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DBUtils {

    /**
     * 批量插入数据库数据
     * @param jdbcTemplate  jdbcTemplate
     * @param entityList    待插入的实体类
     */
    public static <T extends AuditModel> void batchInsert(JdbcTemplate jdbcTemplate, Iterable<T> entityList) {

        Objects.requireNonNull(entityList);
        Iterator<T> iterator = entityList.iterator();
        if (!iterator.hasNext()) {
            throw new IllegalArgumentException("entityList is empty");
        }

        List<T> entityBatchList = new ArrayList<>();
        T sample = iterator.next();
        entityBatchList.add(sample);
        List<Tuple2<String, Method>> fields = ClassUtils.getClassEntityFieldGetter(sample.getClass());
        String tableName = ClassUtils.getEntityTableName(sample.getClass());

        // INSERT INTO xxxx (xx,xx,xx) VALUES (?,?,?),(?,?,?)
        StringBuilder sqlBuilder = new StringBuilder();
        Consumer<StringBuilder> buildInsert = sb ->
                sb.append("INSERT INTO `")
                        .append(tableName).append("` (")
                        .append(fields.stream().map(e -> "`" + e.getT1() + "`").collect(Collectors.joining(",")))
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
                            entity.setId(IdUtil.getId());
                        }
                        if (entity.getCreateAt() == null) {
                            entity.setCreateAt(new Date());
                        }
                        if (entity.getUpdateAt() == null) {
                            entity.setUpdateAt(new Date());
                        }
                        if (entity.getUid() == null) {
                            entity.setUid(SecureUtils.getCurrentUid());
                        }

                        for (Tuple2<String, Method> field : fields) {
                            ps.setObject(idx++, field.getT2().invoke(entity));
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
     * 检查数据库是否为完全空的数据库
     */
    public static boolean isDBEmpty(Connection connection) throws SQLException {

        String name = connection.getMetaData().getDatabaseProductName().toLowerCase();
        if ("mysql".equals(name)) {
            return isMySQLDBEmpty(connection);
        } else if ("sqlite".equals(name)) {
            return isSQLiteDBEmpty(connection);
        } else {
            throw new IllegalArgumentException("不支持的数据库类型：" + name);
        }
    }

    /**
     * 检查数据库是否为完全空的数据库
     */
    private static boolean isSQLiteDBEmpty(Connection connection) throws SQLException {
        Statement stat = connection.createStatement();
        // 获取当前数据库名
        ResultSet res;

        // 获取当前数据库中的所有数据表
        res = stat.executeQuery("SELECT name FROM sqlite_master WHERE type='table'");
        boolean ret = res.next();
        res.close();
        return !ret;
    }

    /**
     * 检查数据库是否为完全空的数据库
     */
    private static boolean isMySQLDBEmpty(Connection connection) throws SQLException {
        Statement stat = connection.createStatement();
        // 获取当前数据库名
        ResultSet res = stat.executeQuery("SELECT database() AS db_name");
        res.next();
        String  dbName = res.getString("db_name");

        // 获取当前数据库中的所有数据表
        res = stat.executeQuery("SELECT table_name FROM information_schema.columns WHERE table_schema = '" + dbName + "' GROUP BY table_name");
        boolean ret = res.next();
        res.close();
        return !ret;
    }

    /**
     * 在新事务中执行Runnable
     * @param propagationBehavior 事务传播行为，使用TransactionDefinition中的常量如PROPAGATION_REQUIRED等
     * @param runnable 要执行的任务
     * @see TransactionDefinition
     */
    public static void executeWithTransactional(int propagationBehavior, Runnable runnable) {
        PlatformTransactionManager transactionManager = SpringContextUtils.getContext().getBean(PlatformTransactionManager.class);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(propagationBehavior);
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(@NotNull TransactionStatus status) {
                runnable.run();
            }
        });
    }

    /**
     * 在新事务中执行Callable并返回结果
     * @param propagationBehavior 事务传播行为，使用TransactionDefinition中的常量如PROPAGATION_REQUIRED等
     * @param callable 要执行的任务，支持返回值
     * @param <T> 返回值类型
     * @return 任务执行结果
     */
    public static <T> T executeWithTransactional(int propagationBehavior, java.util.concurrent.Callable<T> callable) {
        return executeWithTransactional(propagationBehavior, TransactionDefinition.ISOLATION_DEFAULT ,callable);
    }


    /**
     * 在新事务中执行Callable并返回结果
     * @param propagationBehavior 事务传播行为，使用TransactionDefinition中的常量如PROPAGATION_REQUIRED等
     * @param isolationLevel 事务隔离级别 使用TransactionDefinition中的常量如ISOLATION_DEFAULT等
     * @param callable 要执行的任务，支持返回值
     * @param <T> 返回值类型
     * @return 任务执行结果
     */
    public static <T> T executeWithTransactional(int propagationBehavior, int isolationLevel, java.util.concurrent.Callable<T> callable) {
        PlatformTransactionManager transactionManager = SpringContextUtils.getContext().getBean(PlatformTransactionManager.class);
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
}
