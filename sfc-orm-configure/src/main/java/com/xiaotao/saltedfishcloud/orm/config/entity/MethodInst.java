package com.xiaotao.saltedfishcloud.orm.config.entity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MethodInst {
    private final Method method;
    private final Object object;

    public MethodInst(Method method, Object object) {
        this.method = method;
        this.object = object;
    }

    public Object invoke(Object val) throws InvocationTargetException, IllegalAccessException {
        return method.invoke(object, val);
    }
}
