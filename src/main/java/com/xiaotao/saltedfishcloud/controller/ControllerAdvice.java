package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;
import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;
import java.util.List;

/**
 * 全局异常处理，捕获进入控制器的异常并进行处理
 * @TODO 异常时修改HTTP响应码
 */
@Slf4j
@RestControllerAdvice
public class ControllerAdvice {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public JsonResult validFormError(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        StringBuilder sb = new StringBuilder();
        bindingResult.getFieldErrors().forEach(error -> sb.append(error.getDefaultMessage()).append(";"));
        return JsonResult.getInstance(422, null, sb.toString());
    }

    @ExceptionHandler(BindException.class)
    public JsonResult validError(BindException e) {
        List<FieldError> errors = e.getFieldErrors();
        StringBuilder sb = new StringBuilder();
        errors.forEach(error -> sb.append(error.getField()).append(' ').append(error.getDefaultMessage()).append(";"));
        return JsonResult.getInstance(422, null, sb.toString());
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    public JsonResult paramsError(Exception e) {
        return JsonResult.getInstance(422, null, e.getMessage());
    }



    @ExceptionHandler(HasResultException.class)
    public JsonResult handle(HasResultException e) {
        return e.getJsonResult();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public JsonResult handle(AccessDeniedException e) {
        return JsonResult.getInstance(403, null, e.getMessage());
    }

    @ExceptionHandler({
            FileNotFoundException.class,
            NoSuchFileException.class,
            TaskNotFoundException.class
    })
    public JsonResult handle(Exception e) {
        if (log.isDebugEnabled()) {
            e.printStackTrace();
        }
        return JsonResult.getInstance(404, null, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public JsonResult defaultHandle(Exception e) {
        log.error("异常", e);
        return JsonResult.getInstance(500, e.getClass().getCanonicalName(), e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public JsonResult handle(DuplicateKeyException e) {
        return JsonResult.getInstance(-4, null, "资源冲突");
    }
}
