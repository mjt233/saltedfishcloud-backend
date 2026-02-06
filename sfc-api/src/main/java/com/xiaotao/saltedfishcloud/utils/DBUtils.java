package com.xiaotao.saltedfishcloud.utils;

import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DBUtils {
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
        TransactionTemplate transactionTemplate = SpringContextUtils.getContext().getBean(TransactionTemplate.class);
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(propagationBehavior);
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
        TransactionTemplate transactionTemplate = SpringContextUtils.getContext().getBean(TransactionTemplate.class);
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(propagationBehavior);
        return transactionTemplate.execute(new TransactionCallback<T>() {
            @Override
            public T doInTransaction(@NotNull TransactionStatus status) {
                try {
                    return callable.call();
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }
}
