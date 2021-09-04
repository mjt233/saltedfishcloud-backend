package com.xiaotao.saltedfishcloud.utils;

import org.springframework.context.ApplicationContext;

public class SpringContextHolder {
    private static ApplicationContext context;

    public static void setContext(ApplicationContext context) {
        SpringContextHolder.context = context;
    }

    public static ApplicationContext getContext() {
        return context;
    }
}
