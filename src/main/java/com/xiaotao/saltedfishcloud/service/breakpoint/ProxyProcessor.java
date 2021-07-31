package com.xiaotao.saltedfishcloud.service.breakpoint;

import com.xiaotao.saltedfishcloud.service.breakpoint.exception.BreakPointTaskNotFoundException;
import lombok.var;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;

@Component
@Aspect
public class ProxyProcessor {
    @Resource
    private TaskManager manager;

    @Around("@annotation(com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint)")
    public Object proxy(ProceedingJoinPoint pjp) throws Throwable {
        var req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        var id = req.getParameter("breakpoint_id");
        if (id == null) {
            return pjp.proceed();
        }

        var taskInfo = manager.queryTask(id);
        if (taskInfo == null) {
            throw new BreakPointTaskNotFoundException(id);
        }
        var args = pjp.getArgs();

        return pjp.proceed(args);
    }
}
