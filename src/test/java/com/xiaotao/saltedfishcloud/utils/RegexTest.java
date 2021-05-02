package com.xiaotao.saltedfishcloud.utils;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexTest {
    @Test
    void test() {
        Pattern pattern = Pattern.compile("(^\\.+$)|(\\\\)|(/)", Pattern.MULTILINE);
        Assert.assertTrue(pattern.matcher("..").find());
        Assert.assertTrue(pattern.matcher("asd..asdasd/asd").find());
        Assert.assertTrue(pattern.matcher("asd..asdasd\\asd").find());
        Assert.assertTrue(pattern.matcher("asd..as/d/asd\\asd").find());
        Assert.assertFalse(pattern.matcher("asd..asdasdasd").find());
    }
}
