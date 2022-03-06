package com.xiaotao.saltedfishcloud.orm.config.utils;

import com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigEntity;
import com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigKey;
import com.xiaotao.saltedfishcloud.orm.config.entity.ConfigNodeHandler;
import com.xiaotao.saltedfishcloud.orm.config.utils.demo.ConfigClass;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    void testSubKey() {
        @ConfigEntity("test")
        @Data
        class TestClass {
            String name;        // test.name
            Other other;        // test.other.*
            Other other2;       // test.other2.*

            @ConfigKey
            Other other3;       // test.other3.*
            Other2 la;          // test.lalala.*

            @ConfigKey("wow")
            Other2 la2;         // test.wow.*

            Sub1 sub;

            @Data
            class Other {
                Integer age;
            }

            @ConfigEntity("lalala")
            @Data
            class Other2 {
                String name;
            }

            @Data
            class Sub1 {
                String subName1;
                Sub2 subNode;
            }

            @Data
            class Sub2 {
                String subName2;
            }
        }

        final Set<String> allKey = new HashSet<>(ConfigReflectUtils.getAllConfigKey(TestClass.class));
        allKey.forEach(System.out::println);
        assertTrue(allKey.contains("test.name"));
        assertTrue(allKey.contains("test.other.age"));
        assertTrue(allKey.contains("test.other2.age"));
        assertTrue(allKey.contains("test.other3.age"));
        assertTrue(allKey.contains("test.lalala.name"));
        assertTrue(allKey.contains("test.wow.name"));
        assertFalse(allKey.contains("test.other"));
    }

    @Test
    void testSetValue() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, NoSuchFieldException, InstantiationException {
        final ConfigNodeHandler inst = ConfigReflectUtils.getMethodInst("sys.props.city", new ConfigClass());
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
        final ConfigNodeHandler inst = ConfigReflectUtils.getMethodInst("sys.props.name", DEMO_CLASS);
        inst.set(testName);

        assertEquals(testName, DEMO_CLASS.getProps().getName());
        assertEquals(testName, inst.get());
        System.out.println(inst.get());
    }

    @Test
    void testNoneString() throws InvocationTargetException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InstantiationException {
        String testName = "太强了";
        try {
            final ConfigNodeHandler inst = ConfigReflectUtils.getMethodInst("sys.props", DEMO_CLASS);
            fail();
        } catch (IllegalArgumentException ignored) {}

        ConfigNodeHandler inst = ConfigReflectUtils.getMethodInst("sys.intVal", DEMO_CLASS);
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
