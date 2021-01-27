package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.po.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;


public interface UserDao {
    @Select("SELECT * FROM user WHERE user = #{user}")
    User getUserByUser(String user);

//    @Select("SELECT * FROM user WHERE token = #{token}")
//    User getUserByToken(String token);

    @Update("UPDATE user SET last_login = #{loginTime} WHERE id = #{uid}")
    int updateLoginDate(@Param("uid") Integer uid, @Param("loginTime") Long loginTime);

    @Insert("INSERT INTO user (user,pwd,token,type) VALUE (#{user},#{pwd},#{token},#{type})")
    int addUser(@Param("user") String user,
                @Param("pwd") String pwd,
                @Param("token") String token,
                @Param("type") Integer type);
}
