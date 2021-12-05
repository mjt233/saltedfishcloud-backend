package com.xiaotao.saltedfishcloud.dao.mybatis;

import com.xiaotao.saltedfishcloud.entity.po.QuotaInfo;
import com.xiaotao.saltedfishcloud.entity.po.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface UserDao {

    /**
     * 修改用户权限类型
     * @param uid       用户ID
     * @param type      用户类型，1为管理员，0为普通人
     * @return          受影响的行数
     */
    @Update("UPDATE user SET type = #{type} WHERE id = #{uid}")
    int grant(@Param("uid") Integer uid, @Param("type") Integer type);

    /**
     * 获取用户数量
     * @return  用户数
     */
    @Select("SELECT COUNT(*) FROM user")
    int getUserCount();


    /**
     * 修改用户密码
     * @param encodedPassword   经过安全编码后的密码
     * @param uid               用户ID
     * @return                  受影响的数据库行数
     */
    @Update("UPDATE user SET pwd=#{pwd} WHERE id = #{uid}")
    int modifyPassword(@Param("uid") Integer uid, @Param("pwd") String encodedPassword);

    /**
     * 通过用户ID获取用户信息
     * @param id    用户ID
     * @return      用户信息对象
     */
    @Select("SELECT * FROM user WHERE id = #{id}")
    User getUserById(Integer id);

    /**
     * 通过用户名获取用户信息
     * @param user  用户名
     * @return      用户信息对象
     */
    @Select("SELECT * FROM user WHERE user = #{user}")
    User getUserByUser(String user);

    /**
     * 更新用户的最近一次登录日期
     * @param uid           用户ID
     * @param loginTime     登录日期（unix时间戳）
     * @return              受影响的表行数
     */
    @Update("UPDATE user SET last_login = #{loginTime} WHERE id = #{uid}")
    int updateLoginDate(@Param("uid") Integer uid, @Param("loginTime") Long loginTime);

    /**
     * 添加一个用户
     * @param user  用户名
     * @param pwd   安全编码后的密码
     * @param type  用户类型，1为管理员，0为普通用户
     * @return      受影响的表行数
     */
    @Insert("INSERT INTO user (user,pwd,type) VALUE (#{user},#{pwd},#{type})")
    int addUser(@Param("user") String user,
                @Param("pwd") String pwd,
                @Param("type") Integer type);

    /**
     * 取用户列表
     * @return
     */
    @Select("SELECT * FROM user")
    List<User> getUserList();

    /**
     * 获取用户空间配额信息
     * @param uid   用户ID
     * @return      配额信息
     */
    @Select("SELECT used, quota from " +
            "(" +
            "(SELECT sum(size) as used from file_table where uid=#{uid} and size > 0) b," +
            "(SELECT quota from user where id=#{uid} ) a" +
            ")")
    QuotaInfo getUserQuotaUsed(@Param("uid") Integer uid);
}
