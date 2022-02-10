package com.xiaotao.saltedfishcloud.service.breakpoint.utils;

import com.xiaotao.saltedfishcloud.service.breakpoint.PartParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class PartParserTest {

    @Test
    void parse() {
        int[] parse = PartParser.parse("11-13");
        for (int i : parse) {
            System.out.println(i);
        }
    }

    @Test
    void validate() {
        assertTrue(PartParser.validate("1-1"));
        assertFalse(PartParser.validate("1a-1"));
    }
}
