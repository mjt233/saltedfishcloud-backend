package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.securityHandler.LoginFailedHandler;
import com.xiaotao.saltedfishcloud.securityHandler.LoginSuccessHandler;
import com.xiaotao.saltedfishcloud.securityHandler.LogoutSuccessHandler;
import com.xiaotao.saltedfishcloud.service.user.UserDetailsServiceImpl;
import com.xiaotao.saltedfishcloud.utils.MyPasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(jsr250Enabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Resource
    private UserDetailsServiceImpl userDetailsService;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(new MyPasswordEncoder());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/",
                        "/api/public/**",
                        "/api/logout",
                        "/login",
                        "/api/userLogin",
                        "/api/getPublicList/**",
                        "/pubdown/**",
                        "/api/search/public/**",
                        "/api/regUser")
                .permitAll() // 放行主页和公共网盘浏览
                .antMatchers("/private/**").hasRole("COMMON")
                .anyRequest()
                .authenticated();

        http.formLogin()
                .successHandler(new LoginSuccessHandler())
                .failureHandler(new LoginFailedHandler())
                .loginPage("/api/userLogin")
                .usernameParameter("user")
                .passwordParameter("passwd")
                .loginProcessingUrl("/api/User/login").permitAll()
                .and()
                .logout()
                .logoutUrl("/api/logout")
                .logoutSuccessHandler(new LogoutSuccessHandler())
                .permitAll();
        http.csrf().disable()
                .cors().and()
                .servletApi().disable()
                .requestCache().disable();
        http.headers().frameOptions().disable();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        configuration.setAllowedMethods(Arrays.asList("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH"));configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "X-User-Agent", "Content-Type"));
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;

    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/src/**");
    }
}
