package com.xiaotao.saltedfishcloud.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.sfc.cloud.utils.SfcStringUtils;

class StringUtilsTest {

    @Test
    void getURLLastName() {
        String urlLastName = SfcStringUtils.getURLLastName("/a");
        assertEquals("a", urlLastName);

        urlLastName = SfcStringUtils.getURLLastName("/");
        assertNull(urlLastName);

        urlLastName = SfcStringUtils.getURLLastName("a/");
        assertEquals("a", urlLastName);

        urlLastName = SfcStringUtils.getURLLastName("a");
        assertEquals("a", urlLastName);

        urlLastName = SfcStringUtils.getURLLastName("a/?name=xiaotao");
        assertEquals("a", urlLastName);
    }

    @Test
    void testCamelToUnder() {
        assertEquals("shell_execute_record", SfcStringUtils.camelToUnder("ShellExecuteRecord"));
        assertEquals("md5_a114", SfcStringUtils.camelToUnder("md5A114"));
    }

}