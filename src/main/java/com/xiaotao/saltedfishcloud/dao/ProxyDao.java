package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.po.ProxyInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface ProxyDao {
    @Select("SELECT * FROM proxy")
    List<ProxyInfo> getAllProxy();

    @Insert("INSERT INTO proxy (name, type, address, port) VALUES (#{name}, #{type}, #{address}, #{port})")
    int addProxy(ProxyInfo proxy);

    @Update("UPDATE proxy SET name=#{proxy.name}, type=#{proxy.type}, address=#{proxy.address}, port=#{proxy.port} WHERE name=#{name}")
    int modifyProxy(@Param("name") String name,@Param("proxy") ProxyInfo proxy);

    @Delete("DELETE FROM proxy WHERE name = #{name}")
    int removeProxy(String name);

    @Select("SELECT * FROM proxy WHERE name = #{name}")
    ProxyInfo getProxyByName(String name);
}
