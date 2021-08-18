package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.annotation.Resource;
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
    @Resource
    private HttpServletResponse response;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public JsonResult validFormError(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        StringBuilder sb = new StringBuilder();
        bindingResult.getFieldErrors().forEach(error -> sb.append(error.getDefaultMessage()).append(";"));
        return responseError(422, sb.toString());
    }

    @ExceptionHandler(BindException.class)
    public JsonResult validError(BindException e) {
        List<FieldError> errors = e.getFieldErrors();
        StringBuilder sb = new StringBuilder();
        errors.forEach(error -> sb.append(error.getField()).append(' ').append(error.getDefaultMessage()).append(";"));
        return responseError(422, sb.toString());
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    public JsonResult paramsError(Exception e) {
        return responseError(422, e.getMessage());
    }



    @ExceptionHandler(JsonException.class)
    public JsonResult handle(JsonException e) {
        response.setStatus(e.getRes().getCode());
        return e.getRes();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public JsonResult handle(AccessDeniedException e) {
        return responseError(403, e.getMessage());
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
        return responseError(404, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public JsonResult defaultHandle(Exception e) {
        log.error("异常", e);
        return responseError(500, e.getClass().getCanonicalName() + " " + e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public JsonResult handle(DuplicateKeyException e) {
        return responseError(400, e.getMessage());
    }

    private JsonResult responseError(int code, String message) {
        response.setStatus(code);
        return JsonResult.getInstance(code, null, message);
    }

}
