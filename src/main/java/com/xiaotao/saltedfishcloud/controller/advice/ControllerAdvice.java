package com.xiaotao.saltedfishcloud.controller.advice;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
