package com.xiaotao.saltedfishcloud.service.breakpoint;

import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import lombok.var;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 断点续传的代理处理器，处理被注解{@link com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint}标记的控制器方法<br>
 * 当HTTP请求的URI Query String Parameter中包含breakpoint_id时，表示文件上传使用了断点续传<br>
 * 若断点续传任务已完成，将拼装成完整文件，同时构造MultipartFile用于替换控制器对应的原参数，控制器能直接访问处理完成的断点续传文件<br>
 * <br>
 * 控制器方法成功执行无异常后，将会释放对应的断点续传任务数据
 */
@Aspect
public class ProxyProcessor {
    private final TaskManager manager;
    public ProxyProcessor(TaskManager taskManager) {
        manager = taskManager;
    }

    @Around("@annotation(com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint)")
    public Object proxy(ProceedingJoinPoint pjp) throws Throwable {
        var req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        var id = req.getParameter("breakpoint_id");
        if (id == null) {
            return pjp.proceed();
        }

        var taskInfo = manager.queryTask(id);
        if (taskInfo == null) {
            throw new TaskNotFoundException(id);
        }
        var args = pjp.getArgs();

        return pjp.proceed(args);
    }
}
