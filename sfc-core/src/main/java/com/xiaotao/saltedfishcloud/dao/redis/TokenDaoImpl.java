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
public class TokenDaoImpl implements TokenDao {
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisDao redisDao;
    private final UserDao userDao;

    @Override
    public String generateUserToken(Integer uid) {
        final User user = userDao.getUserById(uid);
        if (user == null) { throw new JsonException(AccountError.USER_NOT_EXIST); }
        return generateUserToken(user);
    }

    @Override
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

    @Override
    public void setToken(Integer uid, String token) {
        redisTemplate.opsForValue().set(TokenDao.getTokenKey(uid, token), "1", Duration.ofDays(2));
    }

    @Override
    public void cleanUserToken(Integer uid) {
        redisTemplate.delete(redisDao.scanKeys("xyy::token::" + uid + "::*"));
    }

    @Override
    public boolean isTokenValid(Integer uid, String token) {
        return redisTemplate.opsForValue().get(TokenDao.getTokenKey(uid, token)) != null;
    }

}
