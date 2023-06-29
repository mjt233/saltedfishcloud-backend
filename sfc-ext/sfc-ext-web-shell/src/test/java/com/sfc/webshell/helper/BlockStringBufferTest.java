package com.sfc.webshell.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("块式字符缓冲测试")
class BlockStringBufferTest {

    @Test
    public void testTrim() {
        BlockStringBuffer buffer = new BlockStringBuffer(2, 6);
        // 正常8个字符，最大范围内
        buffer.append("12345678");
        assertEquals(8, buffer.length());
        assertEquals("12345678", buffer.toString());

        // 刚好最大值
        buffer.append("9012");
        assertEquals(12, buffer.length());
        assertEquals("123456789012", buffer.toString());

        // 超出最大值，丢弃一块
        buffer.append("1");
        assertEquals(7, buffer.length());
        assertEquals("7890121", buffer.toString());
    }
}