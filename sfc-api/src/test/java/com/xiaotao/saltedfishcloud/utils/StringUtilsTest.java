package com.xiaotao.saltedfishcloud.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    @Test
    void getURLLastName() {
        String urlLastName = StringUtils.getURLLastName("/a");
        assertEquals("a", urlLastName);

        urlLastName = StringUtils.getURLLastName("/");
        assertNull(urlLastName);

        urlLastName = StringUtils.getURLLastName("a/");
        assertEquals("a", urlLastName);

        urlLastName = StringUtils.getURLLastName("a");
        assertEquals("a", urlLastName);

        urlLastName = StringUtils.getURLLastName("a/?name=xiaotao");
        assertEquals("a", urlLastName);
    }
}