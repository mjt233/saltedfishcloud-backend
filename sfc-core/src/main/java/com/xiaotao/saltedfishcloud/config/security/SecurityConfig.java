package com.xiaotao.saltedfishcloud.config.security;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.config.security.service.UserDetailsServiceImpl;
import com.xiaotao.saltedfishcloud.dao.redis.TokenDaoImpl;
import com.xiaotao.saltedfishcloud.entity.json.JsonResultImpl;
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
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.annotation.Resource;
import java.util.*;

/**
 * SpringSecurity配置类
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(jsr250Enabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private static final String LOGIN_URI = "/api/user/token";
    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;


    @Resource
    private PasswordEncoder myPasswordEncoder;

    @Resource
    private TokenDaoImpl tokenDao;

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
                .antMatchers("/", "/static/**", "/api/static/**", "/favicon.ico", "/index.*").permitAll()
                .antMatchers(getAnonymousUrls()).permitAll()
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


    /**
     * 获取已注册的控制器路由中允许匿名访问的URL
     * @return 允许匿名访问的URL
     */
    public String[] getAnonymousUrls() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
        Set<String> res = new HashSet<>();
        handlerMethods.forEach((info, method) -> {
            AllowAnonymous an = method.getMethod().getAnnotation(AllowAnonymous.class);
            if (an != null) {
                res.addAll(info.getPatternValues());
            }
        });
        return res.toArray(new String[0]);
    }
}
