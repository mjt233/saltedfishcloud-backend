package com.sfc.staticpublish;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRange;

import java.util.List;

public class FileRangeTest {

    @Test
    public void testRangeParse() {
        List<HttpRange> ranges = HttpRange.parseRanges("bytes=0-");
        Assertions.assertEquals(1, ranges.size());
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(1000);
        long end = range.getRangeEnd(1000);
        Assertions.assertEquals(0, start);
        Assertions.assertEquals(999, end);

        range = HttpRange.parseRanges("bytes=0-999").get(0);
        start = range.getRangeStart(1000);
        end = range.getRangeEnd(1000);
        Assertions.assertEquals(0, start);
        Assertions.assertEquals(999, end);


        range = HttpRange.parseRanges("bytes=0-500").get(0);
        start = range.getRangeStart(400);
        end = range.getRangeEnd(400);
        Assertions.assertEquals(0, start);
        Assertions.assertEquals(399, end);


        range = HttpRange.parseRanges("bytes=-500").get(0);
        start = range.getRangeStart(400);
        end = range.getRangeEnd(400);
        Assertions.assertEquals(0, start);
        Assertions.assertEquals(399, end);
    }
}
