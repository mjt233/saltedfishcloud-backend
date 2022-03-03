package com.xiaotao.saltedfishcloud.orm.config.aop;

import com.xiaotao.saltedfishcloud.orm.config.utils.ConfigReflectUtils;
import com.xiaotao.saltedfishcloud.orm.config.ConfigureHandler;
import com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigEntity;
import com.xiaotao.saltedfishcloud.orm.config.enums.EntityType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;

@Aspect
@Slf4j
public class ConfigEntityProxy implements ApplicationRunner {
    @Autowired
    private ConfigureHandler configureHandler;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.debug("[ORM Config]配置自动映射代理服务完成");
    }

    @Around("@within(com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigEntity)")
    @Transactional(rollbackFor = Exception.class)
    public Object handle(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        final MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
        if (!signature.getMethod().getName().startsWith("set")) {
            return proceedingJoinPoint.proceed(proceedingJoinPoint.getArgs());
        }
        final ConfigEntity configEntity = proceedingJoinPoint.getTarget().getClass().getAnnotation(ConfigEntity.class);
        String prefix = configEntity.value();
        String key;
        if (configEntity.type() == EntityType.OBJECT) {
            key = prefix + "." + proceedingJoinPoint.getTarget().getClass().getName();
        } else {
            final String rawName = signature.getMethod().getName();
            key = prefix + "." + ConfigReflectUtils.getFieldNameByMethodName(rawName);
        }

        System.out.println(key);
        return proceedingJoinPoint.proceed(proceedingJoinPoint.getArgs());
    }
}
