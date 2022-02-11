package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.interceptor.ProtectBlocker;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Configuration
public class SpringConfig implements WebMvcConfigurer {
    @Resource
    ProtectBlocker protectBlocker;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(protectBlocker)
                .addPathPatterns("/api/**");
    }

    /**
     * 静态资源路径配置
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**", "/api/static/**")
                .addResourceLocations("classpath:/static/")
                .setUseLastModified(true)
                .setCacheControl(CacheControl.maxAge(24, TimeUnit.HOURS));
    }
}
