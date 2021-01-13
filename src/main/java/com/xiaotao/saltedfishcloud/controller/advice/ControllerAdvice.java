package com.xiaotao.saltedfishcloud.controller.advice;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;

@Slf4j
@RestControllerAdvice
public class ControllerAdvice {
    @ExceptionHandler(HasResultException.class)
    public JsonResult handle(HasResultException e) {
        return e.getJsonResult();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public JsonResult handle(AccessDeniedException e) {
        return JsonResult.getInstance(403, null, e.getMessage());
    }

    @ExceptionHandler(FileNotFoundException.class)
    public JsonResult handle(FileNotFoundException e) {
        return JsonResult.getInstance(404, null, "文件或资源不存在");
    }

    @ExceptionHandler(Exception.class)
    public JsonResult handle(Exception e) {
        log.error("异常", e);
        return JsonResult.getInstance(500, e.getClass().getCanonicalName(), e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public JsonResult handle(DuplicateKeyException e) {
        return JsonResult.getInstance(-4, null, "资源冲突");
    }
}
