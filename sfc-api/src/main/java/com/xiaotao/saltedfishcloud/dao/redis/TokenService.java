package com.xiaotao.saltedfishcloud.dao.redis;

import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.model.vo.UserVO;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;

public interface TokenService {
    /**
     * 获取用户鉴权token在redis中的key
     *
     * @param uid   token对应的用户ID
     * @param token token
     * @return key
     */
    static String getTokenKey(Long uid, String token) {
        return CacheKeyPrefixes.TOKEN + uid + "::" + SecureUtils.getMd5(token);
    }

    /**
     * 使一个用户的token失效
     * @param uid 用户id
     * @param token 该用户的token
     */
    void invalidToken(Long uid, String token);

    /**
     * 创建一个用户token
     *
     * @param uid 用户ID
     * @return 有效的新token
     */
    String generateUserToken(Long uid);

    /**
     * 创建一个用户token
     *
     * @param user 用户信息
     * @return 有效的新token
     */
    String generateUserToken(User user);

    /**
     * 创建一个用户token
     *
     * @param user 用户安全主体
     * @return 有效的新token
     */
    String generateUserToken(UserPrincipal user);

    /**
     * 创建一个用户token
     *
     * @param user 用户信息
     * @return 有效的新token
     */
    String generateUserToken(UserVO user);

    /**
     * 添加用户鉴权token到Redis缓存
     *
     * @param uid   token对应的用户ID
     * @param token token
     */
    void setToken(Long uid, String token);

    /**
     * 清理指定用户的所有已注册token，操作将导致用户需要重新登录
     *
     * @param uid 用户ID
     */
    void cleanUserToken(Long uid);

    /**
     * 判断用户鉴权token是否有效
     *
     * @param uid   token对应的用户ID
     * @param token token
     * @return token有效返回true，否则返回false
     */
    boolean isTokenValid(Long uid, String token);
}
