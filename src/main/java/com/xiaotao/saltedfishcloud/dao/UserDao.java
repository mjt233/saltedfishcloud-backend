package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.po.QuotaInfo;
import com.xiaotao.saltedfishcloud.po.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;


public interface UserDao {
    @Select("SELECT * FROM user WHERE user = #{user}")
    User getUserByUser(String user);

    @Update("UPDATE user SET last_login = #{loginTime} WHERE id = #{uid}")
    int updateLoginDate(@Param("uid") Integer uid, @Param("loginTime") Long loginTime);

    @Insert("INSERT INTO user (user,pwd,type) VALUE (#{user},#{pwd},#{type})")
    int addUser(@Param("user") String user,
                @Param("pwd") String pwd,
                @Param("type") Integer type);


    @Select("SELECT used, quota from " +
            "(" +
            "(SELECT sum(size) as used from file_table where uid=#{uid} and size > 0) b," +
            "(SELECT quota from user where id=#{uid} ) a" +
            ")")
    QuotaInfo getUserQuotaUsed(@Param("uid") Integer uid);
}
