package com.xiaotao.saltedfishcloud.orm.config.utils;

import com.xiaotao.saltedfishcloud.orm.config.entity.MethodInst;
import com.xiaotao.saltedfishcloud.orm.config.utils.demo.ConfigClass;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigUtilsTest {
    private static final ConfigClass DEMO_CLASS;
    static {
        DEMO_CLASS = new ConfigClass();
        DEMO_CLASS.setName("xiaotao");
        final ConfigClass.SomeProperties properties = new ConfigClass.SomeProperties();
        properties.setCity("moon");
        properties.setName("rua");
        DEMO_CLASS.setProps(properties);
    }

    @Test
    void getAllConfigKey() {
        final List<String> allConfigKey = ConfigUtils.getAllConfigKey(DEMO_CLASS.getClass());
        System.out.println(allConfigKey);
    }

    @Test
    void getMethodInst() throws InvocationTargetException, IllegalAccessException {
        String testName = "太强了";
        final MethodInst inst = ConfigUtils.getMethodInst("sys.props.name", DEMO_CLASS);
        inst.invoke(testName);

        assertEquals(DEMO_CLASS.getProps().getName(), testName);
    }
}
