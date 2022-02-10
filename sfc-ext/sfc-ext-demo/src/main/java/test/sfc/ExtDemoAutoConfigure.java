package test.sfc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExtDemoAutoConfigure {

    @Bean
    public DemoController demoController() {
        return new DemoController();
    }
}
