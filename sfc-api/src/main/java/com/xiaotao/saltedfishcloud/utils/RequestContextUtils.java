package com.xiaotao.saltedfishcloud.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@UtilityClass
public class RequestContextUtils {
    public static Optional<HttpServletRequest> getHttpServletRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .map(e -> (ServletRequestAttributes) e)
                .map(ServletRequestAttributes::getRequest);
    }
}
