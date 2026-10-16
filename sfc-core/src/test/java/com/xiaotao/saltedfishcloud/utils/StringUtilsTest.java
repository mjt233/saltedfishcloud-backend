package com.xiaotao.saltedfishcloud.utils;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import com.sfc.cloud.utils.SfcStringUtils;

class StringUtilsTest {
    @Test
    public void testURLParse() {
        assertNull(SfcStringUtils.getURLLastName("http://www.baidu.com/"));
        assertNull(SfcStringUtils.getURLLastName("http://www.baidu.com"));
        assertEquals("a", SfcStringUtils.getURLLastName("http://www.baidu.com/a"));
        assertEquals("dea", SfcStringUtils.getURLLastName("http://www.baidu.com/a/b/d/c/dea?a=1/abcde"));
    }

    @Test
    void getRandomString() {
        System.out.println(SfcStringUtils.getRandomString(32, false));
        System.out.println(SfcStringUtils.getRandomString(32, true));
    }
}
