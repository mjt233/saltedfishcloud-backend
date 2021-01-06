package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.po.FileCacheInfo;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import org.apache.ibatis.annotations.*;

import java.util.Date;
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
    int addPublicCache(@Param("name") String fileName, @Param("path") String path, @Param("size") Long size, @Param("md5") String md5);

    @Insert("INSERT INTO private_file_cache (uid,name,size,md5,path,created_at) VALUES (#{uid},#{name},#{size},#{md5},#{path},NOW())")
    int addPrivateCache(@Param("uid") Integer uid,
                        @Param("name") String fileName,
                        @Param("size") Long size,
                        @Param("md5") String md5,
                        @Param("path") String path);

    @Update("UPDATE private_file_cache SET uid=#{uid},name=#{name},size=#{size},md5=#{md5},created_at=NOW() " +
            "WHERE uid=#{uid} AND path=#{path}")
    int updatePrivateCache(@Param("uid") Integer uid,
                           @Param("name") String fileName,
                           @Param("size") Long size,
                           @Param("md5") String md5,
                           @Param("path") String path);

    @Update("DELETE FROM private_file_cache WHERE uid=#{uid} AND name=#{name} AND path=#{path}")
    int deletePrivateCache(@Param("uid") Integer uid,
                           @Param("name") String fileName,
                           @Param("path") String path);

    @Delete("DELETE FROM private_file_cache WHERE uid=#{uid} AND (path = #{path} OR path like concat(#{path},'/%'))")
    int deletePrivateDirCache(@Param("uid") Integer uid,
                              @Param("path") String path);
}
