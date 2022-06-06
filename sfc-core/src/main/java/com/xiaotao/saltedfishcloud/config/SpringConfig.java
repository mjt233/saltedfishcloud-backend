package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.interceptor.ProtectBlocker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
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
        // 固定添加默认API static静态资源
        registry.addResourceHandler("/api/static/**")
                .addResourceLocations("classpath:/static/")
                .setUseLastModified(true)
                .setCacheControl(CacheControl.maxAge(24, TimeUnit.HOURS));



        // 默认使用static目录的资源作为静态资源，若已集成前端项目，则使用webapp作为站点目录
        String handlerPath = "/**";
        String resourcePath = "classpath:/static/";

        URL defaultIndex = this.getClass().getClassLoader().getResource("webapp/index.html");
        if (defaultIndex != null) {
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
}
