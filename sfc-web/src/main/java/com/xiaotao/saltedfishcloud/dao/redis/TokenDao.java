package com.xiaotao.saltedfishcloud.dao.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.constant.error.AccountError;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TokenDao {
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisDao redisDao;
    private final UserDao userDao;

    /**
     * 创建一个用户token
     * @param uid   用户ID
     * @return      有效的新token
     */
    public String generateUserToken(Integer uid) {
        final User user = userDao.getUserById(uid);
        if (user == null) { throw new JsonException(AccountError.USER_NOT_EXIST); }
        return generateUserToken(user);
    }

    /**
     * 创建一个用户token
     * @param user   用户信息
     * @return      有效的新token
     */
    public String generateUserToken(User user) {
        user.setPwd(null);
        final String token;
        try {
            token = JwtUtils.generateToken(MapperHolder.mapper.writeValueAsString(user), 30 * 24 * 60 * 60);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
        setToken(user.getId(), token);
        return token;
    }

    /**
     * 获取用户鉴权token在redis中的key
     * @param uid token对应的用户ID
     * @param token token
     * @return key
     */
    public static String getTokenKey(Integer uid, String token) {
        return "xyy::token::" + uid + "::" + SecureUtils.getMd5(token);
    }

    /**
     * 添加用户鉴权token到Redis缓存
     * @param uid token对应的用户ID
     * @param token token
     */
    public void setToken(Integer uid, String token) {
        redisTemplate.opsForValue().set(getTokenKey(uid, token), "1", Duration.ofDays(2));
    }

    /**
     * 清理指定用户的所有已注册token，操作将导致用户需要重新登录
     * @param uid  用户ID
     */
    public void cleanUserToken(Integer uid) {
        redisTemplate.delete(redisDao.scanKeys("xyy::token::" + uid + "::*"));
    }

    /**
     * 判断用户鉴权token是否有效
     * @param uid token对应的用户ID
     * @param token token
     * @return  token有效返回true，否则返回false
     */
    public boolean isTokenValid(Integer uid, String token) {
        return redisTemplate.opsForValue().get(getTokenKey(uid, token)) != null;
    }

}
