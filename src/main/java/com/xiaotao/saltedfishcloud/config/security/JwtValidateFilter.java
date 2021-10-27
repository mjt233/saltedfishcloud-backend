package com.xiaotao.saltedfishcloud.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.dao.redis.TokenDao;
import com.xiaotao.saltedfishcloud.po.User;
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
    private final TokenDao tokenDao;

    public JwtValidateFilter(TokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String token = req.getHeader(JwtUtils.AUTHORIZATION);
        if (token == null) {
            token = req.getParameter(JwtUtils.AUTHORIZATION);
        }
        if (token == null || token.length() == 0) {
            chain.doFilter(req, response);
            return;
        } else {
            try {
                // 将其token的负载数据json反序列化为User对象
                User user = MAPPER.readValue(JwtUtils.parse(token), User.class);

                if (tokenDao.isTokenValid(user.getUsername(), token)) {
                    SecurityContextHolder.getContext()
                            .setAuthentication(
                                    new UsernamePasswordAuthenticationToken( user, null, user.getAuthorities())
                            );
                }


            } catch (Exception ignored) {

            }
        }
        chain.doFilter(req, response);
    }

}
