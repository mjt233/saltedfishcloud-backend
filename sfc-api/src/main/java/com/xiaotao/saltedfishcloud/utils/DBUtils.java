package com.xiaotao.saltedfishcloud.utils;

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
}
