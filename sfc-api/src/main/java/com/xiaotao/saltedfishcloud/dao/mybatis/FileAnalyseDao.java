package com.xiaotao.saltedfishcloud.dao.mybatis;

import com.xiaotao.saltedfishcloud.annotations.NullToZero;
import org.apache.ibatis.annotations.Select;

@NullToZero
public interface FileAnalyseDao {

    /**
     * 取用户数据总大小
     */
    @Select("SELECT SUM(size) FROM file_table WHERE size != -1 AND uid != 0 AND (is_mount IS NULL OR is_mount = 0)")
    Long getUserTotalSize();

    /**
     * 取用户数据总大小
     */
    @Select("SELECT SUM(size) FROM file_table WHERE size != -1 AND uid = 0 AND (is_mount IS NULL OR is_mount = 0)")
    Long getPublicTotalSize();

    /**
     * 取目录数
     */
    @Select("SELECT count(*) FROM file_table WHERE uid = 0 AND size = -1 AND (is_mount IS NULL OR is_mount = 0)")
    Long getPublicDirCount();

    /**
     * 取目录数
     */
    @Select("SELECT count(*) FROM file_table WHERE uid != 0 AND size = -1 AND (is_mount IS NULL OR is_mount = 0)")
    Long getUserDirCount();

    /**
     * 取公共网盘文件数
     */
    @Select("SELECT count(*) FROM file_table WHERE uid = 0 AND size != -1 AND (is_mount IS NULL OR is_mount = 0)")
    Long getPublicFileCount();

    /**
     * 取私人网盘文件数
     */
    @Select("SELECT count(*) FROM file_table WHERE uid != 0 AND size != -1 AND (is_mount IS NULL OR is_mount = 0)")
    Long getUserFileCount();
}
