package com.xiaotao.saltedfishcloud.service.breakpoint;

import lombok.var;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Aspect
public class ProxyProcessor {
    @Around("@annotation(com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint)")
    public Object proxy(ProceedingJoinPoint pjp) throws Throwable {
        var req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        if (req.getParameter("breakpoint") == null) {
            return pjp.proceed();
        }

        var id = req.getParameter("breakpoint_id");

        var args = pjp.getArgs();

        return pjp.proceed(args);
    }
}
