package com.sfc.staticpublish.config;

import com.sfc.staticpublish.controller.StaticPublishController;
import com.sfc.staticpublish.model.property.StaticPublishProperty;
import com.sfc.staticpublish.service.impl.StaticPublishRecordServiceImpl;
import com.sfc.staticpublish.service.impl.StaticPublishServiceImpl;
import com.sfc.staticpublish.servlet.DispatchServlet;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring5.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;


@Configuration
@EntityScan("com.sfc.staticpublish.model.po")
@EnableJpaRepositories("com.sfc.staticpublish.repo")
@Import({
        StaticPublishServiceImpl.class,
        StaticPublishRecordServiceImpl.class,
        StaticPublishController.class,
        DispatchServlet.class,
        StaticPublishAutoUpdater.class,
        StaticPublishProperty.class
})
public class StaticPublishAutoConfiguration implements InitializingBean, ApplicationContextAware {
    @Autowired
    private ConfigService configService;

    @Autowired
    private StaticPublishProperty property;

    @Autowired
    private TemplateEngine templateEngine;

    private ApplicationContext context;

    @Override
    public void afterPropertiesSet() throws Exception {
        configService.bindPropertyEntity(property);


        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setPrefix("classpath:/static-publish-templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        resolver.setApplicationContext(context);
        templateEngine.addTemplateResolver(resolver);
    }

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
}
