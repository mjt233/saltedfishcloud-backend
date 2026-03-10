package com.xiaotao.saltedfishcloud.config.security;

import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


/**
 * 在SpringSecurity过滤器链中验证是否存在token且token是否有效，若有效则设置SpringSecurity用户认证信息
 */
@Slf4j
public class JwtValidateFilter extends OncePerRequestFilter {
    private final TokenService tokenDao;

    public JwtValidateFilter(TokenService tokenDao) {
        this.tokenDao = tokenDao;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {

        // 先从header获取token
        String token = SecureUtils.getToken(req);

        // 还是获取不到或获取到个空的token当作无鉴权
        if (token == null || token.isEmpty()) {
            chain.doFilter(req, response);
            return;
        } else {
            // 获取到token
                // 将其token的负载数据json反序列化为User对象
            try {
                User user = MapperHolder.mapper.readValue(JwtUtils.parse(token), User.class);
                user.setToken(token);
                // 判断token是否有效（是否存在redis）
                if (tokenDao.isTokenValid(user.getId(), token)) {
                    // token有效，设置SpringSecurity鉴权上下文
                    SecurityContextHolder.getContext()
                            .setAuthentication(
                                    new UsernamePasswordAuthenticationToken( user, null, user.getAuthorities())
                            );
                }
            } catch (Exception e) {
                log.error("鉴权失败", e);
            }
        }
        chain.doFilter(req, response);
    }

}
