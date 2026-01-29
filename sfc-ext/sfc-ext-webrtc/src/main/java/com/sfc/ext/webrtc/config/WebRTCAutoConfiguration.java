package com.sfc.ext.webrtc.config;

import com.sfc.ext.webrtc.controller.WebRTCSignalingHandler;
import com.sfc.ext.webrtc.model.RTCProperty;
import com.sfc.ext.webrtc.model.RTCPropertyVO;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Import({
        WebRTCSignalingHandler.class
})
@Configuration
public class WebRTCAutoConfiguration {

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        // 这将使请求直接跳过整个 Spring Security 过滤器链
        return (web) -> web.ignoring().requestMatchers("/api/webrtc/ws/**");
    }

    @Bean
    public RTCProperty rtcProperty(ConfigService configService, HelloService helloService) {
        RTCProperty property = new RTCProperty();
//        RTCPropertyVO vo = new RTCPropertyVO(property);
//        BeanUtils.copyProperties(property, vo);

        // 注册绑定配置类
        configService.bindPropertyEntity(property);

        // 将配置信息完全暴露到前端公共参数(后续配置中有敏感数据时再另外使用一个VO暴露)
        helloService.setFeature("rtcConfig", property);
        return property;
    }
}
