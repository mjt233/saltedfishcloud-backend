package test.sfc;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;

@RestController
@RequestMapping("ext")
@Slf4j
public class DemoController implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("[拓展示例]示例控制器已实例化，测试路由：/ext/img, /ext/hello");
    }

    @GetMapping("/img")
    @AllowAnonymous
    public ResponseEntity<Resource> getDemoImg() throws UnsupportedEncodingException {
        return ResourceUtils.wrapResource(new ClassPathResource("extTest.png"));
    }

    @GetMapping("/hello")
    @AllowAnonymous
    public String hello() {
        return "Hello 你好. I'm saltedfishcloud extension 我是咸鱼云拓展";
    }
}
