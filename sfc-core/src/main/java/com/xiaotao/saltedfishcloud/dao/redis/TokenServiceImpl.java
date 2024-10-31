package com.xiaotao.saltedfishcloud.dao.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.constant.error.AccountError;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.vo.UserVO;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisDao redisDao;
    private final UserDao userDao;

    @Override
    public String generateUserToken(Long uid) {
        final User user = userDao.getUserById(uid);
        if (user == null) { throw new JsonException(AccountError.USER_NOT_EXIST); }
        return generateUserToken(UserVO.from(user, true));
    }

    @Override
    public String generateUserToken(User user) {
        return generateUserToken(UserVO.from(user, true));
    }

    @Override
    public String generateUserToken(UserVO user) {
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
    public void setToken(Long uid, String token) {
        redisTemplate.opsForValue().set(TokenService.getTokenKey(uid, token), "1", Duration.ofDays(2));
    }

    @Override
    public void cleanUserToken(Long uid) {
        redisTemplate.delete(redisDao.scanKeys("xyy::token::" + uid + "::*"));
    }

    @Override
    public boolean isTokenValid(Long uid, String token) {
        return redisTemplate.opsForValue().get(TokenService.getTokenKey(uid, token)) != null;
    }

}
