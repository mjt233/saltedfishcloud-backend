package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;

/**
 * 全局异常处理，捕获进入控制器的异常并进行处理
 */
@Slf4j
@RestControllerAdvice
public class ControllerAdvice {
    private static final String LOG_PREFIX = "[GlobalException]";
    @Resource
    private HttpServletResponse response;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public JsonResult validFormError(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        StringBuilder sb = new StringBuilder();
        bindingResult.getFieldErrors().forEach(error -> sb.append(error.getField()).append(error.getDefaultMessage()).append(";"));
        return responseError(422, sb.toString());
    }

    @ExceptionHandler(BindException.class)
    public JsonResult validError(BindException e) {
        List<FieldError> errors = e.getFieldErrors();
        StringBuilder sb = new StringBuilder();
        errors.forEach(error -> sb.append(error.getField()).append(' ').append(error.getDefaultMessage()).append(";"));
        return responseError(422, sb.toString());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public JsonResult requestParamsError(MissingServletRequestParameterException e) {
        return JsonResultImpl.getInstance(422, null, e.getMessage());
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    public JsonResult paramsError(Exception e) {
        if (log.isDebugEnabled()) {
            log.debug("{}校验错误：{}",LOG_PREFIX, e);
        }
        return responseError(422, e.getMessage());
    }



    @ExceptionHandler(JsonException.class)
    public JsonResult handle(JsonException e) {
        if (log.isDebugEnabled()) {
            log.error("{}错误：",LOG_PREFIX, e);
        }
        setStatusCode(e.getRes().getCode());
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

    @ExceptionHandler(IOException.class)
    public Object ioError(HttpServletResponse response, IOException e) {
        if (log.isDebugEnabled()) {
            e.printStackTrace();
        }
        String h = response.getHeader("Content-Type");
        if (h != null) {
            return null;
        } else {
            response.setStatus(500);
            return JsonResultImpl.getInstance(500, null, "Server Error：" + e.getMessage());
        }
    }

    private JsonResult responseError(int code, String message) {
        setStatusCode(code);
        return JsonResultImpl.getInstance(code, null, message);
    }

    private void setStatusCode(int code) {
        response.setStatus(code);
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null && requestAttributes.getResponse() != null) {
            requestAttributes.getResponse().setStatus(code);
        }
    }
}
