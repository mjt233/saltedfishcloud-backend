package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.po.FileCacheInfo;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FileDao {
    @Select("SELECT * FROM file_cache WHERE name like #{key}")
    List<FileCacheInfo> search(String key);

    /**
     * 一个存在SQL注入的查询
     * @param key
     * @return
     */
    @Select("SELECT * FROM file_cache WHERE name like '%${key}%'")
    List<FileCacheInfo> searchWithSqlInject(String key);

    @Insert("INSERT INTO public_file_cache (md5,path,size,name) VALUES (#{md5},#{path},#{size},#{name})")
    int addCache(@Param("name") String fileName, @Param("path") String path, @Param("size") Long size, @Param("md5") String md5);
}
