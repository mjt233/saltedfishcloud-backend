package com.xiaotao.saltedfishcloud.helper;

import com.xiaotao.saltedfishcloud.utils.OSInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class PathBuilderTest {

    @Test()
    void range() {
        PathBuilder pb = new PathBuilder();
        pb.append("////1/\\//\\2\\\\\\\\3/4/5/6/../");
        if (OSInfo.isWindows()) {
            Assertions.assertEquals("1/2/3", pb.range(-2));
            Assertions.assertEquals("1/2", pb.range(2));
            Assertions.assertEquals("/", pb.range(0));
            Assertions.assertEquals("5", pb.range(1,-1));
            Assertions.assertEquals("4/5", pb.range(2,-2));
        } else {
            Assertions.assertEquals("/1/2/3", pb.range(-2));
            Assertions.assertEquals("/1/2", pb.range(2));
            Assertions.assertEquals("/", pb.range(0));
            Assertions.assertEquals("/5", pb.range(1,-1));
            Assertions.assertEquals("/4/5", pb.range(2,-2));
        }

        Assertions.assertEquals("c", pb.clear().append("/a/b/c").range(1, -1));
        Assertions.assertEquals("b", pb.clear().append("/a/b/c").range(1, -2));
        Assertions.assertEquals("a", pb.clear().append("/a/b/c").range(1, -3));
        Assertions.assertEquals("d", pb.clear().append("/a/b/c/d").getPathLast());
        Assertions.assertEquals("c", pb.update("../a/b/c/./d/..").getPathLast());
        Assertions.assertEquals("/a/b/c", PathBuilder.formatPath("../a/b/c/./d/..", true));
        log.info("测试通过！");

    }
}
