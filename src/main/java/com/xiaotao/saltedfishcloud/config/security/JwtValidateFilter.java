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
            String data = (String)JwtUtils.parse(token);
            ObjectMapper mapper = new ObjectMapper();
            User user = mapper.readValue(data, User.class);
            SecurityContextHolder.getContext().setAuthentication( new UsernamePasswordAuthenticationToken( user, null, user.getAuthorities()) );
        }
        chain.doFilter(req, response);
    }

}
