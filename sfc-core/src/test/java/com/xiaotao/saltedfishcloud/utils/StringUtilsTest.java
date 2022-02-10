package com.xiaotao.saltedfishcloud.utils;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StringUtilsTest {
    @Test
    public void testURLParse() {
        try {
            assertNull(StringUtils.getURLLastName("http://www.baidu.com/"));
            assertNull(StringUtils.getURLLastName("http://www.baidu.com"));
            assertEquals("a", StringUtils.getURLLastName("http://www.baidu.com/a"));
            assertEquals("dea", StringUtils.getURLLastName("http://www.baidu.com/a/b/d/c/dea?a=1/abcde"));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    @Test
    void getRandomString() {
        System.out.println(StringUtils.getRandomString(32, false));
        System.out.println(StringUtils.getRandomString(32, true));
    }
}
