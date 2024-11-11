package com.sfc.staticpublish.config;

import com.sfc.staticpublish.controller.StaticPublishController;
import com.sfc.staticpublish.model.property.StaticPublishProperty;
import com.sfc.staticpublish.service.impl.StaticPublishRecordServiceImpl;
import com.sfc.staticpublish.service.impl.StaticPublishServiceImpl;
import com.sfc.staticpublish.servlet.DispatchServlet;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@Configuration
@EntityScan("com.sfc.staticpublish.model.po")
@EnableJpaRepositories("com.sfc.staticpublish.repo")
@Import({
        StaticPublishServiceImpl.class,
        StaticPublishRecordServiceImpl.class,
        StaticPublishController.class,
        DispatchServlet.class,
        StaticPublishProperty.class
})
public class StaticPublishAutoConfiguration implements InitializingBean {
    @Autowired
    private ConfigService configService;

    @Autowired
    private StaticPublishProperty property;

    @Autowired
    private HelloService helloService;

    @Override
    public void afterPropertiesSet() throws Exception {
        configService.bindPropertyEntity(property);

        helloService.setFeature("staticPublish", property);
    }
}
