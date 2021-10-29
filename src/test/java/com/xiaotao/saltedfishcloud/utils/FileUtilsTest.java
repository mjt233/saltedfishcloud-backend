package com.xiaotao.saltedfishcloud.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilsTest {
    @Test
    public void dotest() {
        assertTrue(PathUtils.isSubDir("/asd/asd/a", "/asd/asd/a"));
        assertTrue(PathUtils.isSubDir("/asd/asd/a", "/asd/asd/a/123"));
        assertTrue(PathUtils.isSubDir("/asd/asd/a", "asd/asd/a/123"));
        assertFalse(PathUtils.isSubDir("/asd/asd/a", "/asd/asd"));
        assertFalse(PathUtils.isSubDir("/asd/asd/a", "asd/asd"));
    }
}
