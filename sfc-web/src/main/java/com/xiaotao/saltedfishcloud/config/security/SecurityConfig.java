package com.xiaotao.saltedfishcloud.config.security;

import com.xiaotao.saltedfishcloud.config.security.service.UserDetailsServiceImpl;
import com.xiaotao.saltedfishcloud.dao.redis.TokenDao;
import com.xiaotao.saltedfishcloud.entity.JsonResultImpl;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;

/**
 * SpringSecurity配置类
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(jsr250Enabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private static final String LOGIN_URI = "/api/user/token";
    @Resource
    private SecureUtils secureUtils;

    @Resource
    private PasswordEncoder myPasswordEncoder;

    @Resource
    private TokenDao tokenDao;

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Resource
    private UserDetailsServiceImpl userDetailsService;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(myPasswordEncoder);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);


        //  添加Jwt登录和验证过滤器
        http.addFilterBefore(new JwtLoginFilter(LOGIN_URI, authenticationManagerBean(), tokenDao), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtValidateFilter(tokenDao), UsernamePasswordAuthenticationFilter.class)
                .csrf().disable();

        //  处理过滤器链中出现的异常
        http.exceptionHandling().authenticationEntryPoint((request, response, authException) -> {
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(403);
            response.getWriter().print(JsonResultImpl.getInstance(403, null, "拒绝访问，权限不足或登录已过期/无效"));
        });

        //  放行公共API和登录API
        http.authorizeRequests()
                .antMatchers("/", "/static/**", "/api/static/**").permitAll()
                .antMatchers(secureUtils.getAnonymousUrls()).permitAll()
                .antMatchers(HttpMethod.POST, LOGIN_URI).permitAll()
                .anyRequest().authenticated();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        configuration.setAllowedMethods(Arrays.asList("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH"));configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "X-User-Agent", "Content-Type", "Token"));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;

    }

    @Override
    public void configure(WebSecurity web) {
        web.ignoring().antMatchers("/src/**");
    }
}
