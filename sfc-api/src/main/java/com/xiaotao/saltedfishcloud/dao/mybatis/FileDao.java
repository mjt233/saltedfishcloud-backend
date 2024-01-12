package com.xiaotao.saltedfishcloud.dao.mybatis;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 文件信息dao
 * 后面会逐步迁移到使用jpa
 */
public interface FileDao {
    /**
     * 搜索某个用户的文件
     * @param uid       用户ID
     * @param key       文件名关键字
     * @return      文件信息列表
     */
    List<FileInfo> search(@Param("uid") Long uid, @Param("key") String key);
}
