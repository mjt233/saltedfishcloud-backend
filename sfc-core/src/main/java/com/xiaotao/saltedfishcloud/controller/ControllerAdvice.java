package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.MessageException;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import com.xiaotao.saltedfishcloud.utils.RequestContextUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    public JsonResult<String> validFormError(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        StringBuilder sb = new StringBuilder();
        bindingResult.getFieldErrors().forEach(error -> sb.append(error.getField()).append(error.getDefaultMessage()).append(";"));
        return responseError(422, sb.toString());
    }

    @ExceptionHandler(BindException.class)
    public JsonResult<String> validError(BindException e) {
        List<FieldError> errors = e.getFieldErrors();
        StringBuilder sb = new StringBuilder();
        errors.forEach(error -> sb.append(error.getField()).append(' ').append(error.getDefaultMessage()).append(";"));
        return responseError(422, sb.toString());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public JsonResult<String> requestParamsError(MissingServletRequestParameterException e) {
        return JsonResultImpl.getInstance(422, null, e.getMessage());
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    public JsonResult<String> paramsError(Exception e) {
        if (log.isDebugEnabled()) {
            log.debug("{}校验错误：",LOG_PREFIX, e);
        }
        return responseError(422, e.getMessage());
    }



    @ExceptionHandler(JsonException.class)
    @SuppressWarnings("unchecked")
    public JsonResult<Object> handle(JsonException e, HttpServletResponse response) {
        if (log.isDebugEnabled()) {
            log.error("{}错误：",LOG_PREFIX, e);
        }

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            Boolean isThumbnail = TypeUtils.toBoolean(requestAttributes.getAttribute("isThumbnail", RequestAttributes.SCOPE_REQUEST));
            if (isThumbnail) {
                response.setStatus(404);
                return null;
            }
        }
        if (e.getErrorInfo() != null) {
            setStatusCode(e.getErrorInfo().getStatus());
        }
        return e.getRes();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public JsonResult<String> handle(AccessDeniedException e) {
        return responseError(403, e.getMessage());
    }

    @ExceptionHandler({
            FileNotFoundException.class,
            NoSuchFileException.class,
            TaskNotFoundException.class
    })
    public JsonResult<String> handle(Exception e) {
        log.debug("{}", LOG_PREFIX, e);
        return responseError(404, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public JsonResult<String> defaultHandle(Exception e, HttpServletResponse response) {
        if (e instanceof NoResourceFoundException) {
            log.warn("{} {}", LOG_PREFIX, e.getMessage());
            response.setStatus(404);
            return JsonResultImpl.getInstance(404, 404, null, e.getMessage());
        } else if (isClientAbortException(e)) {
            log.debug("{} 客户端连接中断", LOG_PREFIX);
        } else {
            log.error("异常", e);
        }
        if (e instanceof MessageException) {
            return responseError(500, e.getMessage());
        } else {
            return responseError(500, e.getClass().getCanonicalName() + " " + e.getMessage());
        }

    }

    @ExceptionHandler(DuplicateKeyException.class)
    public JsonResult<String> handle(DuplicateKeyException e) {
        return responseError(400, e.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public Object ioError(HttpServletResponse response, IOException e) {
        if (e instanceof ClientAbortException) {
            log.debug("{} 客户端连接中断", LOG_PREFIX);
        } else if (log.isDebugEnabled()) {
            log.debug("{} IO异常", LOG_PREFIX, e);
        }
        String h = response.getHeader("Content-Type");
        if (h != null) {
            return null;
        } else {
            response.setStatus(500);
            return JsonResultImpl.getInstance(500, null, "Server Error：" + e.getMessage());
        }
    }

    private JsonResult<String> responseError(int code, String message) {
        setStatusCode(code);
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            Boolean isThumbnail = TypeUtils.toBoolean(requestAttributes.getAttribute("isThumbnail", RequestAttributes.SCOPE_REQUEST));
            if (isThumbnail) {
                return null;
            }
        }

        return JsonResultImpl.getInstance(code, null, message);
    }

    private void setStatusCode(int code) {
        response.setStatus(code);
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null && requestAttributes.getResponse() != null) {
            requestAttributes.getResponse().setStatus(code);
        }
    }

    /**
     * 检查异常链中是否包含客户端连接中断异常
     */
    private static boolean isClientAbortException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof ClientAbortException
                    || current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}