package com.xiaotao.saltedfishcloud.service.breakpoint;

import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.MergeFile;
import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import lombok.var;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Parameter;

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
        // 判断是否使用断点续传任务
        var req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        var id = req.getParameter("breakpoint_id");
        // 如果不是断点续传任务，则跳过处理
        if (id == null) {
            return pjp.proceed();
        }

        var taskInfo = manager.queryTask(id);
        if (taskInfo == null) {
            throw new TaskNotFoundException(id);
        }
        var args = pjp.getArgs();
        var sign = pjp.getSignature();

        //  控制器参数偷梁换柱，替换掉被@MergeFile标记的MultipartFile类型参数
        if (sign instanceof MethodSignature) {
            var params = ((MethodSignature) sign).getMethod().getParameters();
            int index = 0;
            for (Parameter param : params) {
                if (param.getAnnotation(MergeFile.class) != null) {
                    args[index] = new MergeMultipartFile(manager.queryTask(id));
                }
                ++index;
            }
        } else {
            throw new UnsupportedOperationException();
        }

        var ret = pjp.proceed(args);
        manager.clear(id);
        return ret;
    }
}
