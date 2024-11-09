package com.xiaotao.saltedfishcloud.service.breakpoint;

import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.MergeFile;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.TaskManager;
import com.xiaotao.saltedfishcloud.service.breakpoint.merge.MergeBreakpointFileProvider;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Parameter;

/**
 * 断点续传的代理处理器，处理被注解{@link BreakPoint}标记的控制器方法<br>
 * 当HTTP请求的URI Query String Parameter中包含breakpoint_id时，表示文件上传使用了断点续传<br>
 * 若断点续传任务已完成，将拼装成完整文件，同时构造MultipartFile用于替换控制器对应的原参数，控制器能直接访问处理完成的断点续传文件<br>
 * <br>
 * 控制器方法成功执行无异常后，将会释放对应的断点续传任务数据
 */
@Aspect
@RequiredArgsConstructor
public class ProxyProcessor {
    private final TaskManager manager;
    private final MergeBreakpointFileProvider provider;

    @Around("@annotation(com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint)")
    public Object proxy(ProceedingJoinPoint pjp) throws Throwable {
        // 判断是否使用断点续传任务
        HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String  id = req.getParameter("breakpoint_id");
        // 如果不是断点续传任务，则跳过处理
        if (id == null) {
            return pjp.proceed();
        }

        TaskMetadata taskInfo = manager.queryTask(id);
        if (taskInfo == null) {
            throw new TaskNotFoundException(id);
        }
        Object[] args = pjp.getArgs();
        Signature sign = pjp.getSignature();

        //  控制器参数偷梁换柱，替换掉被@MergeFile标记的MultipartFile类型参数
        if (sign instanceof MethodSignature) {
            Parameter[] params = ((MethodSignature) sign).getMethod().getParameters();
            int index = 0;
            for (Parameter param : params) {
                if (param.getAnnotation(MergeFile.class) != null) {
                    args[index] = provider.getFile(taskInfo.getTaskId());
                }
                ++index;
            }
        } else {
            throw new UnsupportedOperationException();
        }

        Object ret = pjp.proceed(args);
        manager.clear(id);
        return ret;
    }
}
