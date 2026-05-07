package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.common.RedirectableUrlHttpMessageConverter;
import com.xiaotao.saltedfishcloud.interceptor.ProtectBlocker;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class SpringConfig implements WebMvcConfigurer {
    @Resource
    ProtectBlocker protectBlocker;

    @Bean
    public HttpMessageConverter<Object> redirectableUrlHttpMessageConverter() {
        return new RedirectableUrlHttpMessageConverter();
    }

    @Override
    public void configureMessageConverters(@NotNull List<HttpMessageConverter<?>> converters) {
        SpringContextUtils.setHttpMessageConverterList(converters);
    }

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
        // 固定添加默认API static静态资源
        registry.addResourceHandler("/api/static/**")
                .addResourceLocations("classpath:/static/")
                .setUseLastModified(true)
                .setCacheControl(CacheControl.maxAge(24, TimeUnit.HOURS));



        // 默认使用static目录的资源作为静态资源，若已集成前端项目，则使用webapp作为站点目录
        String handlerPath = "/**";
        String resourcePath = "classpath:/static/";

        if (this.isExistFrontEnd()) {
            log.info("已集成前端项目");
            registry.addResourceHandler("/assets/**")
                    .addResourceLocations("classpath:/webapp/assets/")
                    .setUseLastModified(true)
                    .setCacheControl(CacheControl.maxAge(24, TimeUnit.HOURS));

            resourcePath = "classpath:/webapp/";
        } else {
            log.info("未集成前端项目");
        }

        registry.addResourceHandler(handlerPath)
                .addResourceLocations(resourcePath)
                .setUseLastModified(true)
                .setCacheControl(CacheControl.maxAge(24, TimeUnit.HOURS));
    }

    /**
     * 检查项目资源中是否集成了前端
     */
    private boolean isExistFrontEnd() {
        return this.getClass().getClassLoader().getResource("webapp/index.html") != null;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {

        // 添加/oauth视图路由，直接指向/oauth/index.html
        if (isExistFrontEnd()) {
            registry.addViewController("/oauth")
                    .setViewName("forward:/oauth/index.html");
            registry.addViewController("/oauth/")
                    .setViewName("forward:/oauth/index.html");
        }
    }
}
