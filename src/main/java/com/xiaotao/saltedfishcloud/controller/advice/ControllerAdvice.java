package com.xiaotao.saltedfishcloud.controller.advice;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;
import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;

/**
 * 全局异常处理，捕获进入控制器的异常并进行处理
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

    @ExceptionHandler(ConstraintViolationException.class)
    public JsonResult validFieldError(ConstraintViolationException e) {
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

    @ExceptionHandler({FileNotFoundException.class, NoSuchFileException.class})
    public JsonResult handle(Exception e) {
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
