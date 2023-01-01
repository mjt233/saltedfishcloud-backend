package test.sfc;

import com.xiaotao.saltedfishcloud.common.update.VersionUpdateHandler;
import com.xiaotao.saltedfishcloud.common.update.VersionUpdateManager;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ExtDemoAutoConfigure implements InitializingBean {
    @Autowired
    private VersionUpdateManager versionUpdateManager;

    @Bean
    public DemoController demoController() {
        return new DemoController();
    }

    @Bean
    public DemoUpdater demoUpdater() {
        return new DemoUpdater();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        versionUpdateManager.registerUpdateHandler(new VersionUpdateHandler() {
            @Override
            public void update(Version from, Version to) throws Exception {
                log.info("[插件Demo]版本更新测试，更新到：{}", to);
            }

            @Override
            public Version getUpdateVersion() {
                return Version.valueOf("1.1.0");
            }

            @Override
            public String getScope() {
                return "ext-demo";
            }
        });
    }
}
