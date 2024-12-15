package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.config.SysLogConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyUtilsTest {

    @Test
    void parseLambdaConfigKey() {
        assertEquals("sys.log.enable_log", PropertyUtils.parseLambdaConfigName(SysLogConfig::getEnableLog));
    }
}