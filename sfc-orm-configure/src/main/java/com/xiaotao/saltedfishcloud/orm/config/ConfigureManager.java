package com.xiaotao.saltedfishcloud.orm.config;

import com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ConfigureManager implements InitializingBean {
    private final ConcurrentHashMap<String, MethodClass> cache = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext context;

    private static class MethodClass {
        public Method method;
        public Object object;

        public MethodClass(Method method, Object object) {
            this.method = method;
            this.object = object;
        }
    }

    public void setConfig(String key, String val) {
        try {
            final MethodClass methodClass = cache.get(key);
            if (methodClass == null) {
                throw new IllegalArgumentException("unknown key: " + key);
            }
            methodClass.method.invoke(val);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        final Map<String, Object> beansWithAnnotation = context.getBeansWithAnnotation(ConfigEntity.class);
        beansWithAnnotation.forEach((k,v) -> {
            final ConfigEntity annotation = AnnotationUtils.findAnnotation(v.getClass(), ConfigEntity.class);
            for (Method method : v.getClass().getDeclaredMethods()) {
                if (!method.getName().startsWith("set")) {
                    continue;
                }
                assert annotation != null;
                final String key = annotation.value() + "." + ConfigUtils.getFieldNameByMethodName(method.getName());
                cache.put(key, new MethodClass(method, v));
                log.info("[ORM Config]注册配置节点：{}", key);
            }
        });
        log.debug("[ORM Config]配置自动映射管理器初始化完成");
    }

//    @Override
//    public void run(ApplicationArguments args) throws Exception {
//    }
}
