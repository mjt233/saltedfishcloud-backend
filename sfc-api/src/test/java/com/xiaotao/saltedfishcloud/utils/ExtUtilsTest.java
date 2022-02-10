package com.xiaotao.saltedfishcloud.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ExtUtilsTest {
    private static final String CLASS = "com.saltedfishcloud.ext.hadoop.store.HDFSStoreServiceFactory";

    @Test
    void getExtUrls() {
        System.out.println(Arrays.toString(ExtUtils.getExtUrls()));
    }

    @Test
    void testLoadClass() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        ExtUtils.loadExtJar(ExtUtils.class.getClassLoader());
        final Class<?> clazz = Class.forName("com.xiaotao.test.DemoClass");
        System.out.println(clazz);
    }
}
