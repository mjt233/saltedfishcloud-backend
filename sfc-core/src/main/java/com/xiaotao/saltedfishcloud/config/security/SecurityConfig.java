package com.xiaotao.saltedfishcloud.config.security;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.config.security.service.UserDetailsServiceImpl;
import com.xiaotao.saltedfishcloud.dao.redis.TokenServiceImpl;
import com.xiaotao.saltedfishcloud.helper.Md5PasswordEncoder;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.log.LogRecordManager;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;

/**
 * SpringSecurity配置类
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfig {

    private static final String LOGIN_URI = "/api/user/token";
    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Resource
    private TokenServiceImpl tokenDao;

    @Resource
    private UserDetailsServiceImpl userDetailsService;

    @Resource
    private LogRecordManager logRecordManager;

    @Resource
    private UserService userService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Md5PasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationConfiguration authenticationConfiguration) throws Exception {
        http.cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(corsConfigurationSource()));
        http.sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS));


        //  添加Jwt登录和验证过滤器
        JwtLoginFilter loginFilter = new JwtLoginFilter(LOGIN_URI, authenticationManager(authenticationConfiguration), tokenDao);
        loginFilter.setLogRecordManager(logRecordManager);
        loginFilter.setUserService(userService);
        http.addFilterBefore(loginFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtValidateFilter(tokenDao), UsernamePasswordAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable);

        //  处理过滤器链中出现的异常
        http.exceptionHandling(httpSecurityExceptionHandlingConfigurer -> httpSecurityExceptionHandlingConfigurer
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json;charset=utf-8");
                    response.setStatus(403);
                    response.getWriter().print(JsonResultImpl.getInstance(403, null, "拒绝访问，权限不足或登录已过期/无效"));
                })
        );

        //  放行公共API和登录API
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/","/static-extension.json", "/api/oauth/callback/**","/ext/**", "/assets/**","/static/**", "/api/static/**", "/favicon.ico", "/index.*")
                .permitAll()
                .requestMatchers(getAnonymousUrls()).permitAll()
                .requestMatchers(HttpMethod.POST, LOGIN_URI).permitAll()
                .anyRequest()
                .authenticated()
        );

        http.userDetailsService(userDetailsService);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        configuration.setAllowedMethods(Arrays.asList("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH"));configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "X-User-Agent", "Content-Type", "Token"));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
