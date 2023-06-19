package com.sfc.webshell.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessUtilsTest {

    @Test
    @DisplayName("命令行参数解析-单个参数")
    public void testSingleArg() {
        List<String> args = ProcessUtils.parseCommandArgs("bin");
        assertEquals("bin", args.get(0));
    }


    @Test
    @DisplayName("命令行参数解析-多个参数")
    public void testMultiArg() {
        List<String> args = ProcessUtils.parseCommandArgs("bin aaa bb    cc d");
        assertArrayEquals(
                new String[]{"bin", "aaa" ,"bb", "cc", "d"},
                args.toArray()
        );
    }

    @Test
    @DisplayName("命令行参数解析-字符串长参数")
    public void testStringArg() {
        List<String> args = ProcessUtils.parseCommandArgs("bin \"aaa bb    cc d\"");
        assertArrayEquals(
                new String[]{"bin", "aaa bb    cc d"},
                args.toArray()
        );
    }

    @Test
    @DisplayName("命令行参数解析-字符转义-空格与双引号")
    public void testSpaceEscape() {
        List<String> args = ProcessUtils.parseCommandArgs("bin   \"11\\ 22\" 6666\\\\ 777     \"127.0.0.1\" -t");
        assertArrayEquals(
                new String[]{"bin", "11 22" ,"6666\\", "777", "127.0.0.1", "-t"},
                args.toArray()
        );
    }

    @Test
    @DisplayName("命令行参数解析-字符转义-空格与双引号")
    public void testControlCharEscape() {
        List<String> args = ProcessUtils.parseCommandArgs("bin  \\t \"hello\\r\\nworld\"");
        assertArrayEquals(
                new String[]{"bin", "\t" ,"hello\r\nworld"},
                args.toArray()
        );
    }

    @Test
    @DisplayName("命令行参数解析-字符转义-无效转义")
    public void testNoEscape() {
        List<String> args = ProcessUtils.parseCommandArgs("C:\\Windows\\System32\\PING.exe 127.0.0.1");
        assertArrayEquals(
                new String[]{"C:\\Windows\\System32\\PING.exe", "127.0.0.1"},
                args.toArray()
        );
    }


}