package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StartRecord implements FeatureProvider {
    @Autowired
    private SysProperties sysProperties;

    @Override
    public void registerFeature(HelloService helloService) {
        helloService.setFeature("version", sysProperties.getVersion().toString());
        helloService.setFeature("archiveEncoding", sysProperties.getStore().getArchiveEncoding());
    }
}
