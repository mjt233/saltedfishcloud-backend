package com.xiaotao.saltedfishcloud.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Aspect
public class NullToZeroAspect {
    @Around("@within(com.xiaotao.saltedfishcloud.annotations.NullToZero)")
    public Object check(ProceedingJoinPoint proceedingJoinPoint) {
        Object res = null;
        try {
            res = proceedingJoinPoint.proceed();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return res == null ? 0L : res;
    }
}
