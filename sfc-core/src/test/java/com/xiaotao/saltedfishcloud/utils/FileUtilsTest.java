package com.xiaotao.saltedfishcloud.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {
    @Test
    public void dotest() {
        Assertions.assertTrue(PathUtils.isSubDir("/asd/asd/a", "/asd/asd/a"));
        assertTrue(PathUtils.isSubDir("/asd/asd/a", "/asd/asd/a/123"));
        assertTrue(PathUtils.isSubDir("/asd/asd/a", "asd/asd/a/123"));
        assertFalse(PathUtils.isSubDir("/asd/asd/a", "/asd/asd"));
        assertFalse(PathUtils.isSubDir("/asd/asd/a", "asd/asd"));
    }

    @Test
    public void testParse() {
        String n1 = "a.exe", n2 = ".minecraft", n3 = "kokodayo", n4 = "123.", n5 = ".故意找茬.";
        String[] res = FileUtils.parseName(n1);
        assertEquals("a", res[0]);
        assertEquals("exe", res[1]);

        res = FileUtils.parseName(n2);
        assertEquals("", res[0]);
        assertEquals("minecraft", res[1]);

        res = FileUtils.parseName(n3);
        assertEquals(n3, res[0]);
        assertNull(res[1]);

        res = FileUtils.parseName(n4);
        assertEquals("123", res[0]);
        assertEquals("", res[1]);

        res = FileUtils.parseName(n5);
        assertEquals(".故意找茬", res[0]);
        assertEquals("", res[1]);
    }
}
