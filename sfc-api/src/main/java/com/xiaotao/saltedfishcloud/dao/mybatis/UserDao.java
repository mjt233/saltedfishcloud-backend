package com.xiaotao.saltedfishcloud.dao.mybatis;

import com.xiaotao.saltedfishcloud.model.po.QuotaInfo;
import com.xiaotao.saltedfishcloud.model.po.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

public interface UserDao {

    /**
     * 查询用户基本信息（仅包含id和用户名）
     * @param ids   用户id集合
     * @return      查询结果
     */
    List<User> findBaseInfoByIds(@Param("ids") Collection<Long> ids);

    /**
     * 修改用户的邮箱
     * @param id        用户ID
     * @param email     新邮箱
     * @return          受影响的行数
     */
    @Update("UPDATE user SET email = #{email} WHERE id = #{id}")
    int updateEmail(Long id, String email);

    @Select("SELECT * FROM user WHERE email = #{email}")
    User getByEmail(String email);

    /**
     * 修改用户权限类型
     * @param uid       用户ID
     * @param type      用户类型，1为管理员，0为普通人
     * @return          受影响的行数
     */
    @Update("UPDATE user SET type = #{type} WHERE id = #{uid}")
    int grant(@Param("uid") Long uid, @Param("type") Integer type);

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
    int modifyPassword(@Param("uid") Long uid, @Param("pwd") String encodedPassword);

    /**
     * 通过用户ID获取用户信息
     * @param id    用户ID
     * @return      用户信息对象
     */
    @Select("SELECT * FROM user WHERE id = #{id}")
    User getUserById(Long id);

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
    int updateLoginDate(@Param("uid") Long uid, @Param("loginTime") Long loginTime);

    /**
     * 添加一个用户
     * @param user  用户名
     * @param pwd   安全编码后的密码
     * @param email 用户注册邮箱
     * @param type  用户类型，1为管理员，0为普通用户
     * @return      受影响的表行数
     */
    @Insert("INSERT INTO user (id,user,pwd, email, type, create_at) VALUE (#{id}, #{user},#{pwd}, #{email}, #{type}, NOW())")
    int addUser(@Param("user") String user,
                @Param("pwd") String pwd,
                @Param("email") String email,
                @Param("type") Integer type,
                @Param("id") Long id
    );

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
    QuotaInfo getUserQuotaUsed(@Param("uid") Long uid);
}
