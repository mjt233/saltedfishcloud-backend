package com.xiaotao.saltedfishcloud.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class JwtValidateFilter extends OncePerRequestFilter {

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

                // 解析token，获取其中的负载数据字符串（这里是User对象的json序列化字符串）
                String data = (String)JwtUtils.parse(token);

                // 将其json反序列化为User对象
                ObjectMapper mapper = new ObjectMapper();
                User user = mapper.readValue(data, User.class);
                SecurityContextHolder.getContext().setAuthentication( new UsernamePasswordAuthenticationToken( user, null, user.getAuthorities()) );

            } catch (Exception ignored) {

            }
        }
        chain.doFilter(req, response);
    }

}
