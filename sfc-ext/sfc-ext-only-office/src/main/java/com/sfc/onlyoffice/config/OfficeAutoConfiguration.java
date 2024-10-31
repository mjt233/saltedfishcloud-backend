package com.sfc.onlyoffice.config;

import com.onlyoffice.manager.request.DefaultRequestManager;
import com.onlyoffice.manager.request.RequestManager;
import com.onlyoffice.manager.security.DefaultJwtManager;
import com.onlyoffice.manager.security.JwtManager;
import com.onlyoffice.manager.settings.SettingsManager;
import com.onlyoffice.manager.url.UrlManager;
import com.sfc.onlyoffice.model.OfficeConfigProperty;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring5.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

@Configuration
@Slf4j
@ComponentScan("com.sfc.onlyoffice")
public class OfficeAutoConfiguration implements InitializingBean, ApplicationContextAware {
    @Autowired
    private ConfigService configService;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private OfficeConfigProperty officeConfigProperty;


    private ApplicationContext context;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Bean
    public JwtManager jwtManager(final SettingsManager settingsManager) {
        return new DefaultJwtManager(settingsManager);
    }

    @Bean
    public RequestManager requestManager(final UrlManager urlManager, final JwtManager jwtManager, final SettingsManager settingsManager) {
        return new DefaultRequestManager(urlManager, jwtManager, settingsManager);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setPrefix("classpath:/only-office-templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        resolver.setApplicationContext(context);
        templateEngine.addTemplateResolver(resolver);

        configService.bindPropertyEntity(officeConfigProperty);
        if (StringUtils.hasText(officeConfigProperty.getDocumentServerHost())) {
            log.info("Office插件已就绪");
        } else {
            log.warn("Office插件已加载，但未进行配置");
        }
    }
}
