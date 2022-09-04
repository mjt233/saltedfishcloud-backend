package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.model.po.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class RedisTestService {

    @Cacheable(value = "user", key = "#uid")
    public User getUser(int uid) {
        User user = new User();
        user.setId(uid);
        user.setPwd(uid + "");
        System.out.println("执行");
        return user;
    }

    @Cacheable(value = "token", key = "#token")
    public String setToken(String token) {
        System.out.println("set token");
        return token;
    }

}
