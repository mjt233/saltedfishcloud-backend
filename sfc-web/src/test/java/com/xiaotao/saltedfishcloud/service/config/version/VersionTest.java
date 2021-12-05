package com.xiaotao.saltedfishcloud.service.config.version;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionTest {
    @Test
    public void testVersion() {
        Version v1 = Version.valueOf("1.1.0-SNAPSHOT");
        Version v2 = Version.valueOf("1.0.0-SNAPSHOT");
        Version v3 = Version.valueOf("2.1.0-SNAPSHOT");
        Version v4 = Version.valueOf("1.1.0.0-SNAPSHOT");
        assertEquals(1, v1.getBigVer());
        assertEquals(1, v1.getMdVer());
        assertEquals(0, v1.getSmVer());
        Assertions.assertEquals(VersionTag.SNAPSHOT, v1.getTag());
        assertTrue(v1.compareTo(v2) < 0);
        assertTrue(v1.compareTo(v3) > 0);
        assertTrue(v2.compareTo(v3) > 0);
        assertEquals(0, v1.compareTo(v4));

        assertFalse(v1.isLessThen(v2));
        assertTrue(v2.isLessThen(v1));
        assertFalse(v1.isLessThen(v2, VersionLevel.BIG));
        assertTrue(v4.isLessThen(v3));
    }
}
