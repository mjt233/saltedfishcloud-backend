package com.xiaotao.saltedfishcloud.orm.config.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class TypeUtilsTest {

    @Test
    public void testConvert() {

        // 转换目标为boolean
        assertTrue(TypeUtils.toBoolean("true"));
        assertFalse(TypeUtils.toBoolean("false"));
        assertTrue(TypeUtils.toBoolean(1));
        assertFalse(TypeUtils.toBoolean(0));
        assertTrue(TypeUtils.toBoolean(true));
        assertFalse(TypeUtils.toBoolean(false));

        // 转换目标为数字
        String bigString = "123123123123123123.123";
        String longString = "2147483647000";
        String testNumberStr = "114514";
        int testNumber = 114514;
        assertEquals(testNumber, TypeUtils.convert(Integer.class, testNumberStr).intValue());
        assertEquals(testNumber, TypeUtils.toNumber(BigInteger.class, testNumberStr).intValue());
        assertEquals(testNumber, TypeUtils.toNumber(BigDecimal.class, testNumberStr).intValue());


        // BigInteger、BigDecimal、String与普通数字
        final BigInteger bigInteger = TypeUtils.convert(BigInteger.class, bigString);

        // String -> BigInteger
        assertEquals(bigString.substring(0, bigString.indexOf(".")), bigInteger.toString());

        // String -> BigDecimal
        assertEquals(new BigDecimal(bigString), TypeUtils.toNumber(BigDecimal.class, bigString));

        // String -> Long
        assertEquals(Long.parseLong(longString), TypeUtils.toNumber(Long.class, longString));

        System.out.println(TypeUtils.convert(BigDecimal.class, bigString));

    }
}
