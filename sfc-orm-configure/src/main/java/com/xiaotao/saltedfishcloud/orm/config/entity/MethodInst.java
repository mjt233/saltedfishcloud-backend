package com.xiaotao.saltedfishcloud.orm.config.entity;

import com.xiaotao.saltedfishcloud.orm.config.utils.TypeUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 绑定了对象及其getter与setter方法的对象
 */
public class MethodInst {
    private final Method setter;
    private final Method getter;
    private final Object object;
    private final Class<?> paramClass;

    public MethodInst(Method setter, Method getter, Object object) {
        setter.setAccessible(true);
        getter.setAccessible(true);
        this.setter = setter;
        this.object = object;
        this.getter = getter;

        paramClass = setter.getParameters()[0].getType();
    }

    /**
     * 将值作为setter方法的参数进行调用从而实现对象赋值。
     * 若输入参数与setter方法参数类型不匹配，但都为简单数字类型的情况下，会自动发生转换
     * @param val   待设置的值
     * @return setter方法返回值
     */
    public Object set(Object val) throws InvocationTargetException, IllegalAccessException {
        Object setVal = val;
        if (paramClass != val.getClass()) {
            setVal = TypeUtils.convert(paramClass, val);
        }
        return setter.invoke(object, setVal);
    }

    /**
     * 通过getter方法获取值
     * @return getter方法的返回值
     */
    public Object get() throws InvocationTargetException, IllegalAccessException {
        return getter.invoke(object);
    }
}
