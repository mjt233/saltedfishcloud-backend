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
    void testSetValue() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, NoSuchFieldException, InstantiationException {
        final MethodInst inst = ConfigReflectUtils.getMethodInst("sys.props.city", new ConfigClass());
        assertNull(inst.get());
        inst.set("GuangDong");
        assertEquals("GuangDong", inst.get());
    }

    @Test
    void getAllConfigKey() {
        final List<String> allConfigKey = ConfigReflectUtils.getAllConfigKey(DEMO_CLASS.getClass());
        allConfigKey.forEach(System.out::println);
    }

    @Test
    void getMethodInst() throws InvocationTargetException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InstantiationException {
        String testName = "太强了";
        final MethodInst inst = ConfigReflectUtils.getMethodInst("sys.props.name", DEMO_CLASS);
        inst.set(testName);

        assertEquals(testName, DEMO_CLASS.getProps().getName());
        assertEquals(testName, inst.get());
        System.out.println(inst.get());
    }

    @Test
    void testNoneString() throws InvocationTargetException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InstantiationException {
        String testName = "太强了";
        try {
            final MethodInst inst = ConfigReflectUtils.getMethodInst("sys.props", DEMO_CLASS);
            fail();
        } catch (IllegalArgumentException ignored) {}

        MethodInst inst = ConfigReflectUtils.getMethodInst("sys.intVal", DEMO_CLASS);
        assertNull(inst.get());
        assertNull(inst.set(233));
        assertEquals(233, inst.get());


        inst = ConfigReflectUtils.getMethodInst("sys.longVal", DEMO_CLASS);
        assertNull(inst.get());
        assertNull(inst.set(233));
        assertEquals(233L, inst.get());

        inst = ConfigReflectUtils.getMethodInst("sys.booleanVal", DEMO_CLASS);
        assertNull(inst.get());
        assertNull(inst.set(true));
        assertEquals(true, inst.get());
    }
}
