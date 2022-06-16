package com.xiaotao.saltedfishcloud.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.dao.redis.TokenDaoImpl;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


/**
 * 在SpringSecurity过滤器链中验证是否存在token且token是否有效，若有效则设置SpringSecurity用户认证信息
 */
public class JwtValidateFilter extends OncePerRequestFilter {
    private final static ObjectMapper MAPPER = new ObjectMapper();
    private final TokenDaoImpl tokenDao;

    public JwtValidateFilter(TokenDaoImpl tokenDao) {
        this.tokenDao = tokenDao;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        // 先从header获取token
        String token = req.getHeader(JwtUtils.AUTHORIZATION);
        if (token == null) {
            // 获取不到再从表单获取
            token = req.getParameter(JwtUtils.AUTHORIZATION);
        }

        // 还是获取不到或获取到个空的token当作无鉴权
        if (token == null || token.length() == 0) {
            chain.doFilter(req, response);
            return;
        } else {
            // 获取到token
            try {
                // 将其token的负载数据json反序列化为User对象
                User user = MAPPER.readValue(JwtUtils.parse(token), User.class);

                // 判断token是否有效（是否存在redis）
                if (tokenDao.isTokenValid(user.getId(), token)) {
                    // token有效，设置SpringSecurity鉴权上下文
                    SecurityContextHolder.getContext()
                            .setAuthentication(
                                    new UsernamePasswordAuthenticationToken( user, null, user.getAuthorities())
                            );
                }
            } catch (Exception ignored) { }
        }
        chain.doFilter(req, response);
    }

}
